package mosaic.algebra

import cats.effect._
import cats.implicits._
import scala.collection.mutable.ListBuffer
import scala.sys.process._

/** Agebra for running an external program. */
trait Exec[F[_]] {

  /**
   * Run an external program with arguments, yielding the process output on success, raising an
   * error on nonzero exit.
   */
  def run(cmd: String, args: String*): F[String]

}

object Exec {

  /** Constuct a `Exec` for conforming `F`, given a `log` for messages. */
  def apply[F[_]: Sync](log: Log[F]): Exec[F] =
    new Exec[F] {

      def run(cmd: String, args: String*): F[String] =
        for {
          _ <- log.info(s"$cmd ${args.mkString(" ")}")
          s <- Sync[F].delay(unsafeExec(cmd, args: _*))
          _ <- log.info(s._2)
          _ <- fail(s._1, s._2).whenA(s._1 != 0)
        } yield s._2

      // Fail with an error message.
      def fail[A](exitCode: Int, message: String): F[A] =
        for {
          _ <- log.info(s"Failed (exit code $exitCode).")
          a <- Sync[F].raiseError[A](new RuntimeException(message))
        } yield a

      // Side-effecting execution returning exit code and output.
      private def unsafeExec(cmd: String, args: String*): (Int, String) = {
        val buf = ListBuffer[String]()
        val exitCode = (cmd +: args).cat.run(ProcessLogger(buf.append(_))).exitValue
        (exitCode, buf.mkString("\n"))
      }

    }

}