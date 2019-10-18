package mosaic.algebra

import cats._
import cats.implicits._
import cats.effect._
import java.net.URL
import java.nio.file._
import java.nio.file.StandardCopyOption._
import io.chrisdavenport.log4cats.Logger
import dev.profunktor.redis4cats.algebra.StringCommands
import java.io.InputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.HttpURLConnection.{ HTTP_MOVED_PERM, HTTP_MOVED_TEMP }
import java.net.URLDecoder

/** Algebra for a web client that fetches a batch of URLs. */
trait Fetch[F[_]] {

  /**
   * Fetch many URLs, yielding a path to the resulting directory where the files reside, in no
   * particular order and with no particular name, other than extensions to match the URLs. This is
   * to accommodate Montage tools that wish to be pointed at a directory of FITS files. The
   * directory and its contents will be removed after use.
   */
  def fetchMany[G[_]: Traverse](us: G[URL]): Resource[F, Path] // TODO: weaken to Foldable

}

object Fetch {

  /**
   * Construct a Fetch for conforming `F` given a log, a cache for storing retrieved data, and a
   * source of temporary files. Note that `Par[F]` is available for `IO` by virtue of the implicit
   * `ContextSwitch` introduced by `IOApp`.
   */
  def apply[F[_]: Sync: Parallel](log: Logger[F], cache: Cache[F, URL], temp: Temp[F]): Fetch[F] =
    new Fetch[F] {

      // Our implementation downloads stuff into the cached as needed, and then copies it into
      // another directory so the user can do whatever they want with it, without affecting the
      // cache contents. This may be overly defensive (symbolic links might be sufficient).
      def fetchMany[G[_]: Traverse](us: G[URL]): Resource[F, Path] =
        for {
          _  <- Resource.liftF(log.info(s"${us.size} tiles requested..."))
          d  <- temp.tempDir("mosaic-")
          ps <- Resource.liftF(us.parTraverse(cache.get))
          _  <- ps.traverse(copyInto(_, d))
        } yield d

      // Compute the file extension for a Path, if any, otherwise "". We need this because Montage
      // looks at the file extension to figure out what to do. So we include anything after (and
      // including) the *first* dot in the last path element. So foo/bar/baz.tar.gz yields .tar.gz
      def ext(path: Path): String = {
        val fn = path.getFileName.toString
        fn.indexOf(".") match {
          case -1 => ""
          case n  => fn.substring(n)
        }
      }

      // Resource yielding a copy of `src` (with a new unique name but the same extension) in `dir`.
      // The copy will be deleted after use.
      def copyInto(src: Path, dir: Path): Resource[F, Path] =
        for {
          dest <- temp.tempFileIn(dir, "mosaic-", ext(src))
          _    <- Resource.liftF(Sync[F].delay(Files.copy(src, dest, REPLACE_EXISTING)))
        } yield dest

    }


    // redis caching fetch
    def redis[F[_]: Sync: Parallel](
      log:   Logger[F],
      temp:  Temp[F],
      redis: StringCommands[F, URL, Array[Byte]],
    ): Fetch[F] =
      new Fetch[F] {

        val Prefix = "mosaic-"
        val MaxRedirects = 3

        // Compute the file extension for a URL, if any, otherwise "". We need this because Montage
        // looks at the file extension to figure out what to do. So we include anything after (and
        // including) the *first* dot in the last path element. So foo/bar/baz.tar.gz yields .tar.gz
        def ext(url: URL): String = {
          import Predef._
          url.getPath().split("/").lastOption.fold("") { fn =>
            fn.indexOf(".") match {
              case -1 => ""
              case n  => fn.substring(n)
            }
          }
        }

        // The server started redirecting from HTTP to HTTPS, which java.net.HttpURLConnection will
        // not do. So to handle this we need to read the headers and redirect manually.
        def openStream(url: URL, maxRedirects: Int): F[InputStream] =
          if (maxRedirects < 0)
            Sync[F].raiseError(new IOException("Too many redirects."))
          else
            log.info(s"Trying $url") *>
            Sync[F].defer {
              val conn = url.openConnection().asInstanceOf[HttpURLConnection]
              conn.setInstanceFollowRedirects(false)
              conn.getResponseCode() match {
                case HTTP_MOVED_PERM | HTTP_MOVED_TEMP =>
                  val loc  = URLDecoder.decode(conn.getHeaderField("Location"), "UTF-8");
                  val urlʹ = new URL(url, loc);  // Deal with relative URLs
                  log.info(s"Redirecting to $urlʹ") *> openStream(urlʹ, maxRedirects - 1)
                case _ => conn.getInputStream.pure[F]
              }
            }

        def fetch(url: URL, file: Path): F[Unit] =
          redis.get(url).flatMap {

            case None =>
              for {
                _   <- log.info(s"Cache miss on $url")
                is  <- openStream(url, MaxRedirects)
                _   <- Sync[F].delay(Files.copy(is, file, REPLACE_EXISTING))
                bs  <- Sync[F].delay(Files.readAllBytes(file))
                _   <- redis.set(url, bs)
              } yield ()

            case Some(bs) =>
              log.info(s"Cache hit on $url") *>
              Sync[F].delay(Files.write(file, bs)).void

          }

        def fetchInto(url: URL, dir: Path): F[Path] =
          Sync[F].delay(Files.createTempFile(dir, Prefix, ext(url))).flatTap(fetch(url, _))

        def fetchMany[G[_]: Traverse](us: G[URL]): Resource[F, Path] =
          temp.tempDir(Prefix).evalTap(dir => us.parTraverse_(fetchInto(_, dir)))

      }

}