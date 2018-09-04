package mosaic.algebra

import cats.effect._
import cats.implicits._
import java.net.URLEncoder
import java.nio.file._
import java.nio.file.StandardCopyOption._
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
  def respond(objOrLoc: String, radius: Double, band: Char): F[Long]

}

object HttpMosaic {

  def apply[F[_]: Sync](res: HttpServletResponse, cache: Cache[F, (String, Double, Char)]): HttpMosaic[F] =
    new HttpMosaic[F] {

      def respond(objOrLoc: String, radius: Double, band: Char): F[Long] =
        for {
          p <- cache.get((objOrLoc, radius, band))
          _ <- Sync[F].delay(res.setStatus(HttpServletResponse.SC_OK))
          _ <- Sync[F].delay(res.setContentType("application/fits"))
          _ <- Sync[F].delay(res.setContentLength(p.toFile.length.toInt))
          n <- Sync[F].delay(Files.copy(p, res.getOutputStream))
        } yield n

    }

  def cache[F[_]: Sync](log: Log[F], temp: Temp[F], cacheRoot: Path, mosaic: Mosaic[F]): Cache[F, (String, Double, Char)] = {

    val resolve: ((String, Double, Char)) => Path = { case (objOrLoc, radius, band) =>
      Paths.get(URLEncoder.encode(s"$objOrLoc $radius $band", "US-ASCII")) // lame, do something better
    }

    val fetch: ((String, Double, Char), Path) => F[_] = { case ((objOrLoc, radius, band), dest) =>
      mosaic.mosaic(objOrLoc, radius, band).use { temp =>
        Sync[F].delay(Files.move(temp, dest, REPLACE_EXISTING, ATOMIC_MOVE))
      }
    }

    Cache(log, temp, cacheRoot, resolve, fetch)
  }

}