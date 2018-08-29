package mosaic.server

import cats.effect._
import cats.implicits._
import java.io.IOException
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

/** Algebra for creating temporary filesystem resources that will be deleted after use. */
trait Temp[F[_]] {

  def tempDir(prefix: String): Resource[F, Path]

  def tempFile(prefix: String, suffix: String): Resource[F, Path]

}

object Temp {

  /** Summon the instance for `F`. */
  def apply[F[_]](implicit ev: Temp[F]): ev.type =
    ev

  /** Temp instance for conforming `F`. */
  implicit def instance[F[_]](implicit sf: Sync[F]): Temp[F] =
    new Temp[F] {
      import sf._

      // Taken from the FileVisitor Javadoc
      private object DeletionVisitor extends SimpleFileVisitor[Path] {
        override def visitFile(file: Path, attrs: BasicFileAttributes) = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }
        override def postVisitDirectory(dir: Path, e: IOException) = {
          if (e == null) {
            Files.delete(dir)
            FileVisitResult.CONTINUE
          } else throw e
        }
      }

      def tempDir(prefix: String): Resource[F, Path] =
        Resource.make(delay(Files.createTempDirectory(prefix))) { f =>
          delay(if (f.toFile.exists) Files.walkFileTree(f, DeletionVisitor)).void
        }

      def tempFile(prefix: String, suffix: String): Resource[F, Path] =
        Resource.make(delay(Files.createTempFile(prefix, suffix))) { f =>
          delay(if (f.toFile.exists) Files.delete(f))
        }

    }

}
