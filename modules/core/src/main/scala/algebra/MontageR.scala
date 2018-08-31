package mosaic.algebra

import cats.effect._
import cats.implicits._
import java.nio.file._

/**
 * A higher-level interface for `Montage` that uses temporary files to accumulate FITS files and
 * other output, rather than requiring the user to manage output files. These files are managed via
 * `Resource` and will be deleted after use.
 */
trait MontageR[F[_]] {

  def mArchiveList(survey: String, band: String, objOrLoc: String, width: Double, height: Double): Resource[F, Path]

  def mHdr(objOrLoc: String, radius: Double): Resource[F, Path]

  def mExec(hdr: Path, raw: Path): Resource[F, Path]

}

object MontageR {

  def apply[F[_]: Sync](montage: Montage[F], temp: Temp[F]): MontageR[F] =
    new MontageR[F] {

      def mArchiveList(survey: String, band: String, objOrLoc: String, width: Double, height: Double) =
        for {
          file <- temp.tempFile("mosaic-", ".tbl")
          _    <- Resource.liftF(montage.mArchiveList(survey, band, objOrLoc, width, height, file))
        } yield file

      def mHdr(objOrLoc: String, radius: Double) =
        for {
          hdr <- temp.tempFile("mosaic-", ".hdr")
          _   <- Resource.liftF(montage.mHdr(objOrLoc, radius, hdr))
        } yield hdr

      def mExec(hdr: Path, raw: Path) =
        for {
          wd   <- temp.tempDir("mosaic-work") // a temporary directory that will be cleaned up
          out  =  wd.resolve("mosaic.fits")   // our mosaic'd output
          temp =  wd.resolve("temp")          // a temp directory for Montage to work in
          _    <- Resource.liftF(montage.mExec(out, hdr, raw, temp))
        } yield out

    }
}