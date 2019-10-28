# gsp-montage

This is a server application that addresses the issue where 2MASS tiles overlap the TPE FOV in a non-helpful way, as described in Gemini issue `REL-1093`. Internally it uses [Caltech-IPAC/Montage](https://github.com/Caltech-IPAC/Montage) to fetch tiles and produce composite images. The Gemini Observing Tool uses this service.

This application runs in a Docker container that includes both the Montage binaries and the Scala server. The `Dockerfile` in the root of this project builds the Docker image. See below for information on building and running locally.

This application is deployed on Heroku under the name `gsp-montage`. Gemini HLSW members can examine the application [here](https://dashboard.heroku.com/apps/gsp-montage). Deployment is **automatic**: all pushes to `master` will cause a new version to be deployed to production. You can look at the "Activity" tab in Heroku to see this.

You can try out the production app by swapping `gsp-montage.herokuapp.com` for `localhost:8080` in the `curl` example below.

## Local Development

For local development it's easiest to set up your machine with the services that the Scala program needs. If you just want to run it then skip down to **Running on Docker** below.


- Install [Docker](https://hub.docker.com/editions/community/docker-ce-desktop-mac) if you don't have it already.
- Check out and build [Caltech-IPAC/Montage](https://github.com/Caltech-IPAC/Montage) and make sure the binaries are avaliable on your path.
- Run a local [Redis](https://redis.io) instance: `docker run -p 6379:6379 redis:5.0.0`

Once that stuff is done you can work on it like any other Scala application. If you run the application with `sbt run` or `bloop core run` you can then hit it with `curl` and fetch an image. Here is an example invocation.

```
curl -o /tmp/foo.fits 'http://localhost:8080/v1/mosaic?object=05:51:10.305%2008:10:21.43&radius=0.25&band=H'
```

You can now open `/tmp/foo.fits` in your favorite FITS viewer, if you have one. They all seem to be horrible. You can use the "Open…" menu in the OT's TPE if you want to.

## Running on Docker

If you just want to run this thing locally you can do it by building the same docker image that Heroku builds.

- Install [Docker](https://hub.docker.com/editions/community/docker-ce-desktop-mac) if you don't have it already.
- Run a local [Redis](https://redis.io) instance: `docker run -p 6379:6379 redis:5.0.0`

You can now build the gsp-montage image (it will be slow the first time).

    docker build -t gsp-montage

And run a container using the image you just made.

    docker run -t -i -p 8080:8080 -e PORT=8080 -e REDIS_URL=redis://host.docker.internal gsp-montage

The same `curl` command above in **Local Development** should now work.