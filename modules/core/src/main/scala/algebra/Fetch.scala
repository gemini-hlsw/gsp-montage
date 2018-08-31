package mosaic.algebra

import cats._
import cats.implicits._
import cats.effect._
import cats.temp.par._
import java.net.URL
import java.nio.file._
import java.nio.file.StandardCopyOption._

/** Algebra for a web client that fetches a batch of URLs. */
trait Fetch[F[_]] {

  /**
   * Fetch many URLs, yielding a path to the resulting directory where the files reside, in no
   * particular order and with no particular name, other than extensions to match the URLs. This is
   * to accommodate Montage tools that wish to be pointed at a directory of FITS files. The
   * directory and its contents will be removed after use.
   */
  def fetchMany[G[_]: Traverse](us: G[URL]): Resource[F, Path]

}

object Fetch {

  /** A caching implementation. */
  def apply[F[_]: Sync: Par](log: Log[F], cache: Cache[F, URL], temp: Temp[F]): Fetch[F] =
    new Fetch[F] {

      // Our implementation downloads stuff as needed and then copies it into a new temp directory
      // so the user can do whatever they want without affecting the cache itself.
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

      // Copy `src` to a new uniquely-named file in `dir`, preserving the original file extension.
      def copyInto(src: Path, dir: Path): Resource[F, Unit] =
        for {
          dest <- temp.tempFileIn(dir, "mosaic-", ext(src))
          _    <- Resource.liftF(Sync[F].delay(Files.copy(src, dest, REPLACE_EXISTING)))
        } yield ()

    }

}