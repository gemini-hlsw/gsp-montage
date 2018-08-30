package mosaic.server

import cats.effect._
import cats.implicits._
import java.net.URL
import java.nio.file._

/** Algebra for creating mosaic images. */
trait Mosaic[F[_]] {

  def mosaic(objOrLoc: String, radius: Double, band: Char): Resource[F, Path]

}

object Mosaic {

  def apply[F[_]](lf: Log[F], mf: Montage[F], cf: Cache[F])(
    implicit tf: Temp[F],
             sf: Concurrent[F]
  ): Mosaic[F] =
    new Mosaic[F] {
      import mf._, tf._, lf._, sf._

      implicit class StructOps(fs: F[Struct]) {
        def require: F[Unit] =
          fs.ensureOr(s => new RuntimeException(s.toMap.toString))(_.isOk).void
      }

      def archiveList(survey: String, band: String, objOrLoc: String, width: Double, height: Double): Resource[F, Path] =
        for {
          tbl <- tempFile("mosaic-", ".tbl")
          _   <- Resource.liftF(mArchiveList(survey, band, objOrLoc, width, height, tbl).require)
        } yield tbl

      def hdr(objOrLoc: String, radius: Double): Resource[F, Path] =
        for {
          hdr <- tempFile("mosaic-", ".hdr")
          _   <- Resource.liftF(mHdr(objOrLoc, radius, hdr).require)
        } yield hdr

      /** Build the mosaic using the specified header and raw files, yielding a fits file. */
      def exec(hdr: Path, raw: Path): Resource[F, Path] =
        for {
          wd   <- tempDir("mosaic-work")
          out  =  wd.resolve("mosaic.fits")
          temp =  wd.resolve("temp")
          _    <- Resource.liftF(mExec(out, hdr, raw, temp))
        } yield out

      /**
       * Fetch all tiles from `Table`, yielding the temporary directory where they reside, which
       * will contain nothing else and will be removed after use.
       */
      def raw(t: Table): Resource[F, Path] =
        t.get("URL") match {
          case None => Resource.liftF(raiseError[Path](new Exception("Table has no URL column.")))
          case Some(ss) => cf.fetchMany(ss.map(new URL(_)))
        }

      def mosaic(objOrLoc: String, radius: Double, band: Char): Resource[F, Path] =
        for {
          _    <- Resource.liftF(info(s"""mosaic("$objOrLoc", $radius, '$band')"""))
          tbl  <- archiveList("2MASS", band.toString, objOrLoc, radius, radius)
          t2   <- Resource.liftF(Table.read(tbl))
          raw  <- raw(t2)
          hdr  <- hdr(objOrLoc, radius)
          fits <- exec(hdr, raw)
        } yield fits

    }

}