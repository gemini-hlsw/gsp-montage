// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package mosaic.algebra

import cats.effect._
import cats.implicits._
import java.nio.file._
import mosaic.data.Struct

/**
 * Algebra for shelling out to Montage programs. Montage output is a string-encoded record
 * indicating various things about execution, and FITS files and other things are written to files
 * specified by the caller. So these computations all yield a decoded `Struct` with the
 * understanding that on successful execution (this is the only case you need to handle; non-zero
 * result codes raise an error) files specified by `out` will contain something useful.
 */
trait Montage[F[_]] {

  def mArchiveList(survey: String, band: String, objOrLoc: String, width: Double, height: Double, out: Path): F[Struct]

  def mHdr(objOrLoc: String, radius: Double, out: Path): F[Struct]

  def mExec(out: Path, header: Path, raw: Path, tempDir: Path): F[Struct]

}

object Montage {

  /** Constuct a `Montage` for conforming `F`, given an `Exec` for running external programs. */
  def apply[F[_]: Sync](exec: Exec[F]): Montage[F] =
    new Montage[F] {

      def mHdr(objOrLoc: String, radius: Double, out: Path) =
        runMontage(
          "mHdr",
          objOrLoc,
          radius.toString,
          out.toString
        )

      def mExec(out: Path, header: Path, raw: Path, tempDir: Path) =
        runMontage(
          "mExec",
          "-o", out.toString,
          "-f", header.toString,
          "-r", raw.toString,
          tempDir.toString
        )

      def mArchiveList(survey: String, band: String, objOrLoc: String, width: Double, height: Double, out: Path) =
        runMontage(
          "mArchiveList",
          survey,
          band,
          objOrLoc,
          width.toString,
          height.toString,
          out.toString
        )

       // Exec a montage command with args, yielding the output Struct, raising an error if the
       // output can't be parsed.
      def runMontage(cmd: String, args: String*): F[Struct] =
        exec.run(cmd, args: _*).map(Struct.parse).flatMap {
          case Left(err)     => Sync[F].raiseError(new RuntimeException(err))
          case Right(struct) => struct.pure[F]
        }

    }

}
