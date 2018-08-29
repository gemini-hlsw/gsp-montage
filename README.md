## mosaic-server

This is a partial fix for the issue where 2MASS tiles overlap the FOV in a non-helpful way, as described in Gemini issue `REL-1093`.

This requies a local install of my Montage fork on the `mArchiveList-segfault` branch. There was a [PR](https://github.com/Caltech-IPAC/Montage/pull/32) back to Montage that may have been merged by now, so check it before proceeding.

It also requires a patched OT with the change on my Ocs fork on the `tpe-fixes` branch, and a hacked `ImageCatalog.scala` thus:

```patch
@@ -88,7 +88,8 @@ abstract class AstroCatalog(id: CatalogId, displayName: String, shortName: Strin
   def adjacentOverlap: Angle = Angle.zero

   override def queryUrl(c: Coordinates, site: Option[Site]): NonEmptyList[URL] =
-    NonEmptyList(new URL(s" http://irsa.ipac.caltech.edu/cgi-bin/Oasis/2MASSImg/nph-2massimg?objstr=${c.ra.toAngle.formatHMS}%20${c.dec.formatDMS}&size=${size.toArcsecs.toInt}&band=${band.name}"))
+  //  NonEmptyList(new URL(s" http://irsa.ipac.caltech.edu/cgi-bin/Oasis/2MASSImg/nph-2massimg?objstr=${c.ra.toAngle.formatHMS}%20${c.dec.formatDMS}&size=${size.toArcsecs.toInt}&band=${band.name}"))
+    NonEmptyList(new URL(s" http://localhost:8080/?object=${c.ra.toAngle.formatHMS}%20${c.dec.formatDMS}&radius=${0.25}&band=${band.name}"))
 }
```

Notes:

- I'm using fs2/cats-effect milestones in order to get `Bracket` and `Resource`. http4s isn't quite ready yet so I'm using Jetty for now on the front end.

Next steps:

- Run `mArchiveList` explicitly and download tiles in parallel.
- Add a tile cache.
- Add a mosaic cache (is this necessary? assembling small mosaics is pretty fast)

Deployment:

- Unclear how to deploy this since it has a native component. Docker?
