package mosaic.server

import atto._, Atto._
import cats.effect._
import cats.implicits._
import java.nio.file._

final class Table(headers: List[Table.Header], data: List[String]) {

  def get(name: String): Option[List[String]] =
    headers.find(_.name == name).map { case Table.Header(_, start, end) =>
      data.map(_.substring(start, end).trim)
    }

}

object Table {

  final case class Header(name: String, start: Int, end: Int)

  val parser: Parser[Table] = {

    val pipe   = char('|')
    val space  = char(' ')
    val spaces = many(space)
    val eol    = char('\n')
    val name   = stringOf1(noneOf("|\n"))

    def header(bol: Int): Parser[Header] =
      for {
        p0 <- pos
        _  <- pipe
        _  <- spaces
        n  <- name
        p1 <- pos
      } yield Header(n, p0 - bol, p1 - bol)

    val headers =
      for {
        bol <- pos
        hs  <- many(header(bol))
        _   <- pipe
        _   <- eol
      } yield hs

    val line =
      stringOf1(notChar('\n')) <~ eol

    for {
      _    <- string("\\datatype=fitshdr\n")
      hs   <- headers
      _    <- headers // ignore types for now
      data <- many(line)
    } yield new Table(hs, data)

  }

  def parse(s: String): Either[String, Table] =
    parser.parseOnly(s).either

  def read[F[_]: Sync](p: Path): F[Table] =
    Sync[F].delay(new String(Files.readAllBytes(p), "UTF-8")).map(parse).flatMap {
      case Left(s)  => Sync[F].raiseError(new RuntimeException(s))
      case Right(t) => Sync[F].pure(t)
    }


}