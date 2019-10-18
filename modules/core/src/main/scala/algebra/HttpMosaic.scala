package mosaic.algebra

import cats.effect._
import cats.implicits._
import java.net.URLEncoder
import java.nio.file._
import java.nio.file.StandardCopyOption._
import javax.servlet.http.HttpServletResponse
import io.chrisdavenport.log4cats.Logger

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

  /**
   * Construct an HttpMosaic for the given response, given a cache keyed on mosaic arguments.
   * This isn't fully baked … we need a data type for query parameters here.
   */
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

  /**
   * Construct a cache for mosaics given a log, a source of temporary files, a filesystem root for
   * cached mosaics, and a `Mosaic` instance to do the real work.
   */
  def cache[F[_]: Sync](log: Logger[F], temp: Temp[F], cacheRoot: Path, mosaic: Mosaic[F]): Cache[F, (String, Double, Char)] = {

    // Convert mosaic arguments into a filesystem path by URL-encoding them. This is bad because
    // everything will end up in the same directory. What we really want is to partition by RA or
    // something, but we don't have that structure yet (objOrLoc is an arbitrary string).
    val resolve: ((String, Double, Char)) => Path = { case (objOrLoc, radius, band) =>
      Paths.get(URLEncoder.encode(s"$objOrLoc $radius $band", "US-ASCII"))
    }

    // Delegate to the mosaic instance to generate the temporary output file, then cache it by
    // moving it to the destination path.
    val fetch: ((String, Double, Char), Path) => F[_] = { case ((objOrLoc, radius, band), dest) =>
      mosaic.mosaic(objOrLoc, radius, band).use { temp =>
        Sync[F].delay(Files.move(temp, dest, REPLACE_EXISTING, ATOMIC_MOVE))
      }
    }

    // And that's all we need!
    Cache(log, temp, cacheRoot, resolve, fetch)

  }

}