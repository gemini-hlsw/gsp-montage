package mosaic.algebra

import cats.effect._
import cats.implicits._
import java.nio.file.Files
import javax.servlet.http.HttpServletResponse

/** Algebra for assembling a mosaic and sinking it to an `HttpServletResponse`. */
trait HttpMosaic[F[_]] {

  /**
   * Construct a rectangular FITS image large enough to contain a circle of `radius` degrees
   * (typically 0.25; i.e., 15") centered at the given location (typically an RA/DEC string like
   * "04:55:10.305 07:55:25.43") in the requested band (`J`, `H`, or `K`) and send it to the
   * given `HttpServletResponse`, setting headers as needed, yielding the total number of bytes
   * sent.
   */
  def respond(objOrLoc: String, radius: Double, band: Char, res: HttpServletResponse): F[Long]

}

object HttpMosaic {

  def apply[F[_]: Sync](mosaic: Mosaic[F]): HttpMosaic[F] =
    new HttpMosaic[F] {

      def respond(objOrLoc: String, radius: Double, band: Char, res: HttpServletResponse): F[Long] =
        mosaic.mosaic(objOrLoc, radius, band).use { path =>
          for {
            _ <- Sync[F].delay(res.setStatus(HttpServletResponse.SC_OK))
            _ <- Sync[F].delay(res.setContentType("application/fits"))
            _ <- Sync[F].delay(res.setContentLength(path.toFile.length.toInt))
            n <- Sync[F].delay(Files.copy(path, res.getOutputStream))
          } yield n
        }

    }

}