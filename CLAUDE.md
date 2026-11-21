# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An HTTP server that builds 2MASS mosaic FITS images by shelling out to [Caltech-IPAC/Montage](https://github.com/Caltech-IPAC/Montage) binaries (fixes Gemini issue `REL-1093`, where 2MASS tiles overlap the TPE field of view badly). The Gemini Observing Tool calls it.

Single endpoint:

```
GET /v1/mosaic?object=<RA DEC or name>&radius=<deg, default 0.25>&band=<J|H|K>
```

## Environment

Use the Nix flake (`nix develop`, or direnv via `.envrc`). It provides JDK 17, sbt and scala-cli. JDK 17 is required: sbt-lucuma fails the build on older JDKs.

Running locally also needs:
- Montage binaries (`mArchiveList`, `mHdr`, `mExec`) on `PATH`
- Redis at `$REDIS_URL` (default `redis://localhost`), e.g. `docker run -p 6379:6379 redis:7.0.10`

## Commands

```
sbt core/run                    # server on $PORT (default 8080)
sbt core/docker:stage           # render Dockerfile to modules/core/target/docker/stage
sbt docker:publishLocal         # build image registry.heroku.com/gsp-montage/web
```

There are no tests. If sbt dies resolving dependencies with "Attempts to download ... twice in parallel", retry or run with `CI=true` (coursier progress-logger bug in sbt 1.9.0).

Smoke test (use a fresh Redis, otherwise cached tiles skip the HTTPS fetch path):

```
curl -o /tmp/foo.fits 'http://localhost:8080/v1/mosaic?object=05:51:10.305%2008:10:21.43&radius=0.25&band=H'
```

Run the built image:

```
docker run -p 8080:8080 -e PORT=8080 -e REDIS_URL=redis://host.docker.internal registry.heroku.com/gsp-montage/web
```

## Docker image and deployment

The image is defined entirely by `dockerCommands` in `build.sbt` (sbt-native-packager); there is no Dockerfile or `heroku.yml`. It builds Montage from source at a pinned commit (later commits segfault). Building that commit on Ubuntu Noble needs:
- `libnsl-dev`
- a `/usr/local/bin/gcc` wrapper adding `-fcommon` (GCC 10+ rejects Montage's duplicate globals; overriding `CC` on the `make` line breaks the include paths baked into Montage's Makefiles)

`dockerBuildOptions` forces `linux/amd64` to match Heroku. Montage's bundled libraries can't configure on ARM, so don't drop it on Apple Silicon. Expect a slow build under emulation.

Deploy is manual and requires Heroku collaborator access on `gsp-montage`:

```
heroku container:login
sbt core/docker:publish
heroku container:release web -a gsp-montage
```

Check the runtime JVM with `heroku run -a gsp-montage -- java -version`.

## Architecture

Scala 3.3, cats-effect 3 / fs2 3 / http4s 1.0 milestone with Blaze, redis4cats, tagless-final style. Everything lives in `modules/core`, package `mosaic`.

Compiled with `-Yno-predef`: `scala.Predef` is not auto-imported, so files import it explicitly. Source files carry the AURA copyright header.

Each algebra in `algebra/` is a `trait X[F[_]]` with a companion `apply` building the live interpreter. `Main.mosaic` wires them together per request:

- `Exec` runs an external process, fails on nonzero exit.
- `Montage` wraps the three Montage CLIs and parses their stdout into `data.Struct` (atto parser).
- `MontageR` lifts `Montage` into `Resource`s backed by `Temp` files/dirs, so all intermediate files are cleaned up automatically.
- `Fetch` downloads tile URLs in parallel into a temp dir via `HttpURLConnection`. Follows HTTP→HTTPS redirects manually (`HttpURLConnection` won't), so TLS trust comes from the image's Java CA store. Keeps the URL's full extension because Montage dispatches on it.
- `Mosaic` is the pipeline: `mArchiveList` → parse `data.Table` (IPAC table) → fetch the `URL` column → `mHdr` → `mExec` → output FITS path.
- `HttpMosaic` turns that into an fs2 byte stream for the response.

Redis is a two-level cache with `URL` keys and `Array[Byte]` values (custom lettuce codec in `Main.codec`):
- `HttpMosaic` caches the final FITS keyed by the request URI (normalized to `http://0.0.0.0` + path/query).
- `Fetch` caches each downloaded 2MASS tile keyed by its source URL.

The Redis client disables TLS peer verification (`setVerifyPeer(false)`), unlike tile fetches.

Logging: `Main.newLog` creates a per-request logger that prefixes each line with a request counter and elapsed ms.
