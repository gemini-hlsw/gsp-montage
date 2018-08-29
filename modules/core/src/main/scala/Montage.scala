package mosaic.server

import cats.effect._
import cats.implicits._
import java.nio.file._
import scala.collection.mutable.ListBuffer
import scala.sys.process._

/** Algebra for shelling out to Montage programs. */
trait Montage[F[_]] {

  def mHdr(objOrLoc: String, radius: Double, out: Path): F[Struct]

  def mExec(out: Path, header: Path, band: Char, tempDir: Path): F[Struct]

}

object Montage {

  /** Summon the instance for `F`. */
  def apply[F[_]](implicit ev: Montage[F]): Montage[F] =
    ev

  /** Montage instance for any conforming `F`. */
  implicit def instance[F[_]](
    implicit sf: Sync[F]
  ): Montage[F] =
    new Montage[F] {
      import sf._

      def mHdr(objOrLoc: String, radius: Double, out: Path): F[Struct] =
        montage(
          "mHdr",
          objOrLoc,
          radius.toString,
          out.toString
        )

      def mExec(out: Path, header: Path, band: Char, tempDir: Path): F[Struct] =
        montage(
          "mExec",
          "-c",
          "-o", out.toString,
          "-f", header.toString,
          "2MASS", band.toString,
          tempDir.toString
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
        delay {
          Console.println(s"*** EXEC $cmd ${args.mkString(" ")}")
          val buf = ListBuffer[String]()
          val exitCode = (cmd +: args).cat.run(ProcessLogger(buf.append(_))).exitValue
          (exitCode, buf.mkString("\n"))
        }

    }

}
