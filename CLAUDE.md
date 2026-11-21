# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An HTTP server that builds 2MASS mosaic FITS images by shelling out to [Caltech-IPAC/Montage](https://github.com/Caltech-IPAC/Montage) binaries (fixes Gemini issue `REL-1093`, where 2MASS tiles overlap the TPE field of view badly). The Gemini Observing Tool calls it.

Single endpoint:

```
GET /v1/mosaic?object=<RA DEC or name>&radius=<deg, default 0.25>&band=<J|H|K>
```

## Commands

```
sbt compile
sbt headerCheck compile test   # what CI (.travis.yml) runs
sbt run                        # starts server on $PORT (default 8080)
sbt core/stage                 # what the Dockerfile builds
```

There are no tests yet. `headerCheck` (from `sbt-gsp`) requires every source file to start with the AURA copyright header; copy it from an existing file. `sbt headerCreate` adds it.

Running locally needs:
- Montage binaries (`mArchiveList`, `mHdr`, `mExec`) on `PATH`
- Redis at `$REDIS_URL` (default `redis://localhost`), e.g. `docker run -p 6379:6379 redis:5.0.0`

Smoke test:

```
curl -o /tmp/foo.fits 'http://localhost:8080/v1/mosaic?object=05:51:10.305%2008:10:21.43&radius=0.25&band=H'
```

Docker (same image Heroku builds):

```
docker build -t gsp-montage .
docker run -t -i -p 8080:8080 -e PORT=8080 -e REDIS_URL=redis://host.docker.internal gsp-montage
```

## Deployment

Deployed on Heroku as `gsp-montage` via `heroku.yml` + root `Dockerfile`. **Every push to `master` auto-deploys to production.** The `dockerCommands` block in `build.sbt` is a separate, older sbt-native-packager path; the root `Dockerfile` is what Heroku actually uses.

## Architecture

Scala 2.13, cats-effect 2 / fs2 2 / http4s 0.21 (milestone), tagless-final style. Everything lives in `modules/core`, package `mosaic`.

Compiled with `-Yno-predef`: `scala.Predef` is not auto-imported, so files import it explicitly (e.g. `import scala.Predef._` or selectively).

Each algebra in `algebra/` is a `trait X[F[_]]` with a companion `apply` building the live interpreter. `Main.mosaic` wires them together per request:

- `Exec` runs an external process on the `Blocker`, fails on nonzero exit.
- `Montage` wraps the three Montage CLIs and parses their stdout into `data.Struct` (atto parser).
- `MontageR` lifts `Montage` into `Resource`s backed by `Temp` files/dirs, so all intermediate files are cleaned up automatically.
- `Fetch` downloads tile URLs in parallel into a temp dir. Follows HTTP→HTTPS redirects manually (`HttpURLConnection` won't). Keeps the URL's full extension because Montage dispatches on it.
- `Mosaic` is the pipeline: `mArchiveList` → parse `data.Table` (IPAC table) → fetch the `URL` column → `mHdr` → `mExec` → output FITS path.
- `HttpMosaic` turns that into an fs2 byte stream for the response.

Redis is a two-level cache with `URL` keys and `Array[Byte]` values (custom lettuce codec in `Main.codec`):
- `HttpMosaic` caches the final FITS keyed by the request URI (normalized to `http://0.0.0.0` + path/query).
- `Fetch` caches each downloaded 2MASS tile keyed by its source URL.

Logging: `Main.newLog` creates a per-request logger that prefixes each line with a request counter and elapsed ms.
