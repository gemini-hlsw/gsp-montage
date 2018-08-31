package mosaic.algebra

import cats.effect._
import cats.implicits._
import java.net.URL
import java.nio.file._
import java.nio.file.StandardCopyOption._

/** A file cache keyed on values `K`. */
trait Cache[F[_], K] {

  /**
   * Return a path to the cache entry for `key`, which will be fetched if it's not present. The
   * cache implementation defines what this means. There is no notion of a missing key; if data
   * cannot be fetched then it's an error condition.
   */
  def get(key: K): F[Path]

}

object Cache {

  /**
   * Construct an unbounded cache given a log, a mechanism to create temporary files, a root
   * directory for cache storage, a way to resolve keys to paths, and a way to fetch data for a
   * given key into a file. This is the most general constructor.
   */
  def apply[F[_]: Sync, K](
    log:       Log[F],
    temp:      Temp[F],
    cacheRoot: Path,
    resolve:   K => Path,
    fetch:     (K, Path) => F[_]
  ): Cache[F, K] =
    new Cache[F, K] {

      // Fetch `key`'s data into `out`, using a temporary file to avoid observing partially-written
      // files. The final move *should* be atomic, so we should never get a cache hit for anything
      // other than a complete file.
      def doFetch(key: K, dest: Path): F[Unit] =
        temp.tempFile("cache-", ".temp").use { temp =>
          for {
            _ <- Sync[F].delay(Files.createDirectories(dest.getParent))
            _ <- fetch(key, temp)
            _ <- Sync[F].delay(Files.move(temp, dest, REPLACE_EXISTING, ATOMIC_MOVE))
            _ <- log.info(s"Cached $key")
          } yield ()
        }

      def get(key: K): F[Path] = {
        val cachePath = cacheRoot.resolve(resolve(key))
        Sync[F].delay(cachePath.toFile.exists).flatMap {
          case true  => log.info(s"Hit $key").as(cachePath)
          case false => doFetch(key, cachePath).as(cachePath)
        }
      }

    }

  /**
   * Construct a URL-keyed cache given a log, a mechanism to create temporary files, and a root
   * directory for cache storage. Files will be stored under `cacheRoot` in the same form they
   * appear in URLs, discarding any query arguments.
   */
  def urlCache[F[_]: Sync, K](
    log:       Log[F],
    temp:      Temp[F],
    cacheRoot: Path
  ): Cache[F, URL] = {

    // Compute the filesystem path where `url` will be cached, which is just the cache root
    // followed by the URL path segments. URLs we deal with are plain files so we don't worry
    // about looking for a query string.
    val path: URL => Path = url =>
      cacheRoot.resolve(Paths.get(url.getHost, url.getPath.split("/"): _*))

    // Fetch the URL contents into the given file path.
    val fetch: (URL, Path) => F[Long] = (url, path) =>
      Sync[F].delay(Files.copy(url.openStream, path, REPLACE_EXISTING))

    apply(log, temp, cacheRoot, path, fetch)

  }

}