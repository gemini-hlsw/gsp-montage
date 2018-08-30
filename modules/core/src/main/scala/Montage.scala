package mosaic.server

import cats.effect._
import cats.implicits._
import java.nio.file._
import scala.collection.mutable.ListBuffer
import scala.sys.process._

/** Algebra for shelling out to Montage programs. */
trait Montage[F[_]] {

  def mArchiveList(survey: String, band: String, objOrLoc: String, width: Double, height: Double, outfile: Path): F[Struct]

  def mHdr(objOrLoc: String, radius: Double, out: Path): F[Struct]

  def mExec(out: Path, header: Path, raw: Path, tempDir: Path): F[Struct]

}

object Montage {

  def apply[F[_]](log: Log[F])(
    implicit sf: Sync[F]
  ): Montage[F] =
    new Montage[F] {
      import sf._

      def mHdr(objOrLoc: String, radius: Double, out: Path) =
        montage(
          "mHdr",
          objOrLoc,
          radius.toString,
          out.toString
        )

      def mExec(out: Path, header: Path, raw: Path, tempDir: Path) =
        montage(
          "mExec",
          "-o", out.toString,
          "-f", header.toString,
          "-r", raw.toString,
          tempDir.toString
        )

      def mArchiveList(survey: String, band: String, objOrLoc: String, width: Double, height: Double, outfile: Path) =
        montage(
          "mArchiveList",
          survey,
          band,
          objOrLoc,
          width.toString,
          height.toString,
          outfile.toString
        )

      /**
       * Exec a montage command with args, yielding the output Struct, raising an error if the
       * output can't be parsed.
       */
      def montage(cmd: String, args: String*): F[Struct] =
        exec(cmd, args: _*).flatMap { case (_, output) =>
          Struct.parse(output) match {
            case Left(err)     => raiseError(new RuntimeException(err))
            case Right(struct) => struct.pure[F]
          }
        }

      /** Exec a command with args, yielding the exit code and combined stdout/stderr output. */
      def exec(cmd: String, args: String*): F[(Int, String)] =
        for {
          _ <- log.info(s"$cmd ${args.mkString(" ")}")
          s <- delay(unsafeExec(cmd, args: _*))
          _ <- log.info(s.toString)
        } yield s

      private def unsafeExec(cmd: String, args: String*): (Int, String) = {
        val buf = ListBuffer[String]()
        val exitCode = (cmd +: args).cat.run(ProcessLogger(buf.append(_))).exitValue
        (exitCode, buf.mkString("\n"))
      }

    }

}
