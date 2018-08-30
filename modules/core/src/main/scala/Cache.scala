package mosaic.server

import cats._
import cats.implicits._
import cats.effect._
import cats.effect.implicits._
import java.net.URL
import java.nio.file._

/** An algebra for a cached web client. */
trait Cache[F[_]] {

  /**
   * Fetch a URL, yielding a path to the resulting file, which will be removed after use. Files
   * are fetched on demand if they do not reside in the cache.
   */
  def fetch(url: URL): Resource[F, Path]

  /**
   * Fetch many URLs, yielding a path to the resulting directory, which will be removed after use.
   * Files are fetched on demand if they do not reside in the cache.
   */
  def fetchMany[G[_]: Traverse](us: G[URL]): Resource[F, Path]

}

object Cache {

  def apply[F[_]](lf: Log[F], cacheRoot: Path)(
    implicit sf: Concurrent[F],
             tf: Temp[F]
  ): Cache[F] =
    new Cache[F] {
      import sf._, lf._, tf._

      /** Filesystem path where this URL will be cached. */
      private def path(url: URL): Path =
        cacheRoot.resolve(Paths.get(url.getHost, url.getPath.split("/"): _*))

      /** Download `url` into `out`, using a tempfile to avoid observing partially-written files. */
      private def rawFetch(url: URL, out: Path): F[Unit] =
        tempFile("mosaic-", ".temp").use { temp =>
          for {
            _ <- delay(Files.copy(url.openStream, temp, StandardCopyOption.REPLACE_EXISTING))
            _ <- delay(Files.move(temp, out, StandardCopyOption.REPLACE_EXISTING))
          } yield ()
        }

      /** Download `url` if necessary and and return a path into the cache. */
      private def readOrFetch(url: URL): F[Path] = {
        val p = path(url)
        if (p.toFile.exists)
          info(s"Hit: $url").as(p)
        else
          for {
            _ <- delay(Files.createDirectories(p.getParent))
            _ <- rawFetch(url, p)
            _ <- info(s"Cached $url")
          } yield p
      }

      /** Download `url` if necessary and and return a path into the cache. */
      private def readOrFetchMany[G[_]: Traverse](us: G[URL]): F[G[Path]] =
        us.traverse(u => readOrFetch(u).start).flatMap(fs => fs.traverse(_.join))

      def fetch(url: URL): Resource[F, Path] =
        for {
          f <- tempFile("mosaic-", ".cached")
          c <- Resource.liftF(readOrFetch(url))
          _ <- Resource.liftF(delay(Files.copy(c, f, StandardCopyOption.REPLACE_EXISTING)))
        } yield c

      def fetchMany[G[_]: Traverse](us: G[URL]): Resource[F, Path] =
        for {
          d  <- tempDir("mosaic-")
          ps <- Resource.liftF(readOrFetchMany(us))
          _  <- ps.traverse { p =>
                  for {
                    f <- tempFileIn(d, "mosaic-", ".fits.gz") // oops, we need to copy the extension from the url
                    _ <- Resource.liftF(delay(Files.copy(p, f, StandardCopyOption.REPLACE_EXISTING)))
                  } yield ()
                }
        } yield d

    }
}