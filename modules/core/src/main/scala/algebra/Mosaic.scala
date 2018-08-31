package mosaic.algebra

import cats.effect._
import cats.implicits._
import java.net.URL
import java.nio.file._
import mosaic.data.Table

/** Algebra for creating mosaic images. */
trait Mosaic[F[_]] {

  /**
   * Construct a resource that yields a rectangular FITS image large enough to contain a circle of
   * `radius` degrees (typically 0.25; i.e., 15") centered at the given location (typically an
   * RA/DEC string like "04:55:10.305 07:55:25.43") in the requested band (`J`, `H`, or `K`)
   */
  def mosaic(objOrLoc: String, radius: Double, band: Char): Resource[F, Path]

}

object Mosaic {

  def apply[F[_]: Sync](log: Log[F], montage: MontageR[F], fetch: Fetch[F]): Mosaic[F] =
    new Mosaic[F] {

      // Use mArchiveList to identify the tiles we need, fetch them, then create a header and
      // assemble a mosaic.
      def mosaic(objOrLoc: String, radius: Double, band: Char): Resource[F, Path] =
        for {
          _    <- Resource.liftF(log.info(s"""mosaic("$objOrLoc", $radius, '$band')"""))
          tbl  <- montage.mArchiveList("2MASS", band.toString, objOrLoc, radius, radius)
          t2   <- Resource.liftF(Table.read(tbl))
          dir  <- fetchTiles(t2)
          hdr  <- montage.mHdr(objOrLoc, radius)
          fits <- montage.mExec(hdr, dir)
        } yield fits

      // Fetch all tiles from `Table`, yielding the temporary directory where they reside, which
      // will contain nothing else and will be removed after use.
      def fetchTiles(t: Table): Resource[F, Path] =
        t.get("URL") match {
          case None => Resource.liftF(Sync[F].raiseError[Path](new Exception("Table has no URL column.")))
          case Some(ss) => fetch.fetchMany(ss.map(new URL(_)))
        }

    }

}