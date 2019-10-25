## mosaic-server

trivial change

This is a partial fix for the issue where 2MASS tiles overlap the FOV in a non-helpful way, as described in Gemini issue `REL-1093`.

It should work for you if you patch your OT's `ImageCatalog.scala` thus:

```patch
@@ -88,7 +88,8 @@ abstract class AstroCatalog(id: CatalogId, displayName: String, shortName: Strin
   def adjacentOverlap: Angle = Angle.zero

   override def queryUrl(c: Coordinates, site: Option[Site]): NonEmptyList[URL] =
-    NonEmptyList(new URL(s" http://irsa.ipac.caltech.edu/cgi-bin/Oasis/2MASSImg/nph-2massimg?objstr=${c.ra.toAngle.formatHMS}%20${c.dec.formatDMS}&size=${size.toArcsecs.toInt}&band=${band.name}"))
+  //  NonEmptyList(new URL(s" http://irsa.ipac.caltech.edu/cgi-bin/Oasis/2MASSImg/nph-2massimg?objstr=${c.ra.toAngle.formatHMS}%20${c.dec.formatDMS}&size=${size.toArcsecs.toInt}&band=${band.name}"))
+    NonEmptyList(new URL(s" http://gemini-2mass-mosaic.herokuapp.com/v1/mosaic?object=${c.ra.toAngle.formatHMS}%20${c.dec.formatDMS}&radius=${0.25}&band=${band.name}"))
 }
```

### Running Locally

First you need to check out and build [Caltech-IPAC/Montage](https://github.com/Caltech-IPAC/Montage) and make sure it's on your path. You can test this by typing `mHdr` with no args and you should get an error message back.

You also need a local Redis instance, which you can start up thus:

```
docker run -p 6379:6379 redis:5.0.0
```

And then `sbt core/run` or `bloop run core`. An example invocation is:

```
curl -o /tmp/foo.fits 'http://localhost:8080/v1/mosaic?object=05:51:10.305%2008:10:21.43&radius=0.25&band=H'
```

### Running on Heroku

This only works for Rob. Anyone else needs to be [added as a collaborator](https://devcenter.heroku.com/articles/collaborating#add-collaborators).

To release a new version to Heroku do:

```
heroku container:login
sbt core/docker:publish
heroku container:release web
```

An example invocation is:

```
curl -o /tmp/foo.fits 'http://gemini-2mass-mosaic.herokuapp.com/v1/mosaic?object=05:51:10.305%2008:10:21.43&radius=0.25&band=H'
```


### Next Steps

Next steps:

- Move cache to S3 … the dyno will run out of disk quickly
- I'm using fs2/cats-effect milestones in order to get `Bracket` and `Resource`. http4s isn't quite ready yet so I'm using Jetty for now on the front end.
