package mosaic.algebra

import cats.effect._
import cats.implicits._
import java.nio.file._
import io.chrisdavenport.log4cats.Logger
import fs2.Stream
import dev.profunktor.redis4cats.algebra.StringCommands
import java.net.URL
import fs2.Chunk

/** Algebra for assembling a mosaic and sinking it to an `HttpServletResponse`. */
trait HttpMosaic[F[_]] {

  /**
   * Construct a rectangular FITS image large enough to contain a circle of `radius` degrees
   * (typically 0.25; i.e., 15") centered at the given location (typically an RA/DEC string like
   * "04:55:10.305 07:55:25.43") in the requested band (`J`, `H`, or `K`) and send it to the
   * given `HttpServletResponse`, setting headers as needed, yielding the total number of bytes
   * sent.
   */
  def stream(objOrLoc: String, radius: Double, band: Char): Stream[F, Byte]

}

object HttpMosaic {

  def apply[F[_]: Sync](log: Logger[F], redis: StringCommands[F, URL, Array[Byte]], url: URL, mosaic: Mosaic[F]): HttpMosaic[F] =
    new HttpMosaic[F] {
      def stream(objOrLoc: String, radius: Double, band: Char): Stream[F,Byte] = {
        Stream.eval(redis.get(url)) flatMap {
          case Some(arr) =>
            Stream.eval(log.info(s"Cache hit on $url")) *>
            Stream.chunk(Chunk.bytes(arr))
          case None      =>
            for {
              _    <- Stream.eval(log.info(s"Cache miss on $url"))
              path <- Stream.resource(mosaic.mosaic(objOrLoc, radius, band))
              arr  <- Stream.eval(Sync[F].delay(Files.readAllBytes(path)))
              _    <- Stream.eval(redis.set(url, arr))
              b    <- Stream.chunk(Chunk.bytes(arr))
            } yield b
          }
        }
    }

}