package mosaic.server

import cats._
import cats.effect._
import cats.implicits._
import java.nio.file._

/** Algebra for creating mosaic images. */
trait Mosaic[F[_]] {

  def mosaic(objOrLoc: String, radius: Double, band: Char): Resource[F, Path]

}

object Mosaic {

  /** Summon the instance for `F`. */
  def apply[F[_]](implicit ev: Mosaic[F]): Mosaic[F] =
    ev

  /** Mosaic instance for conforming `F`. */
  implicit def instance[F[_]](
    implicit mf: Montage[F],
             tf: Temp[F],
             ef: MonadError[F, Throwable]
  ): Mosaic[F] =
    new Mosaic[F] {
      import mf._, tf._

      implicit class StructOps(fs: F[Struct]) {
        def require: F[Unit] =
          fs.ensureOr(s => new RuntimeException(s.toMap.toString))(_.isOk).void
      }

      def mHdrRsrc(objOrLoc: String, radius: Double): Resource[F, Path] =
        for {
          hdr <- tempFile("mosaic-", ".hdr")
          _   <- Resource.liftF(mHdr(objOrLoc, radius, hdr).require)
        } yield hdr

      def mExecTempRsrc(header: Path, band: Char): Resource[F, Path] =
        for {
          out <- tempFile("mosaic-", ".fits")
          dir <- tempDir("mosaic-")
          _   <- Resource.liftF(mExec(out, header, band, dir).require)
        } yield out

      def mosaic(objOrLoc: String, radius: Double, band: Char): Resource[F, Path] =
        mHdrRsrc(objOrLoc, radius).flatMap(mExecTempRsrc(_, band))

    }

}