// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package mosaic.algebra

import cats.effect._
import cats.implicits._
import java.io.IOException
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

/** Algebra for creating temporary filesystem resources that will be deleted after use. */
trait Temp[F[_]] {

  /**
   * Resource yielding a new temporary directory with the given prefix. The directory and its
   * contents will be deleted after use.
   */
  def tempDir(prefix: String): Resource[F, Path]

  /**
   * Resource yielding a new temporary file with the given prefix and suffix. The file will be
   * deleted after use.
   */
  def tempFile(prefix: String, suffix: String): Resource[F, Path]

  /**
   * Resource yielding a new temporary file with the given prefix and suffix, created in the given
   * directory. The file will be deleted after use.
   */
  def tempFileIn(dir: Path, prefix: String, suffix: String): Resource[F, Path]

}

object Temp {

  /** Temp instance for conforming `F`. */
  def apply[F[_]: Sync]: Temp[F] =
    new Temp[F] {

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

      def tempDir(prefix: String) =
        Resource.make(Sync[F].delay(Files.createTempDirectory(prefix))) { f =>
          Sync[F].delay(if (f.toFile.exists) Files.walkFileTree(f, DeletionVisitor)).void
        }

      def tempFile(prefix: String, suffix: String) =
        Resource.make(Sync[F].delay(Files.createTempFile(prefix, suffix))) { f =>
          Sync[F].delay(if (f.toFile.exists) Files.delete(f))
        }

      def tempFileIn(path: Path, prefix: String, suffix: String) =
        Resource.make(Sync[F].delay(Files.createTempFile(path, prefix, suffix))) { f =>
          Sync[F].delay(if (f.toFile.exists) Files.delete(f))
        }

    }

}
