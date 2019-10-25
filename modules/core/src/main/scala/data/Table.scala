// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package mosaic.data

import atto._, Atto._
import cats.effect._
import cats.implicits._
import java.nio.file._

/** FITS table, with accessor by column. That's all we need for now. */
final class Table(headers: List[Table.Header], data: List[String]) {

  def get(name: String): Option[List[String]] =
    headers.find(_.name == name).map { case Table.Header(_, start, end) =>
      data.map(_.substring(start, end).trim)
    }

}

object Table {

  /** A column header with a name and column offsets where values can be found. */
  final case class Header(name: String, start: Int, end: Int)

  /** Parser for a FITS table. */ // see example input at the end of the source file.
  val parser: Parser[Table] = {

    val pipe   = char('|')
    val space  = char(' ')
    val spaces = many(space)
    val eol    = char('\n')
    val name   = stringOf1(noneOf("|\n"))
    val line   = stringOf1(notChar('\n')) <~ eol

    def header(bol: Int): Parser[Header] =
      for {
        p0 <- pos
        _  <- pipe
        _  <- spaces
        n  <- name
        p1 <- pos
      } yield Header(n, p0 - bol, p1 - bol)

    val headers: Parser[List[Header]] =
      for {
        bol <- pos
        hs  <- many(header(bol))
        _   <- pipe
        _   <- eol
      } yield hs

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

/* Example input below (first char is the backslash; last char is a newline after the last filename)

\datatype=fitshdr
|   cntr|  ctype1|  ctype2|naxis1|naxis2|    crval1|    crval2| crpix1| crpix2|     cdelt1|     cdelt2|    crota2|     scale|                                                                                         URL|                                file|
|    int|    char|    char|   int|   int|    double|    double| double| double|     double|     double|    double|    double|                                                                                        char|                                char|
       1 RA---SIN DEC--SIN    512   1024  73.580911   7.285234  256.50  512.50 -0.00027778  0.00027778    0.01113    1.10969 http://irsa.ipac.caltech.edu/ibe/data/twomass/full/full/991214n/s076/image/hi0760209.fits.gz 2mass-atlas-991214n-h0760209.fits.gz
       2 RA---SIN DEC--SIN    512   1024  73.823722   7.555147  256.50  512.50 -0.00027778  0.00027778   -0.00405    1.11030 http://irsa.ipac.caltech.edu/ibe/data/twomass/full/full/991214n/s084/image/hi0840197.fits.gz 2mass-atlas-991214n-h0840197.fits.gz
       3 RA---SIN DEC--SIN    512   1024  73.945015   7.610907  256.50  512.50 -0.00027778  0.00027778    0.01211    1.11071 http://irsa.ipac.caltech.edu/ibe/data/twomass/full/full/991214n/s085/image/hi0850079.fits.gz 2mass-atlas-991214n-h0850079.fits.gz
       4 RA---SIN DEC--SIN    512   1024  73.823702   7.285703  256.50  512.50 -0.00027778  0.00027778   -0.00404    1.11040 http://irsa.ipac.caltech.edu/ibe/data/twomass/full/full/991214n/s084/image/hi0840209.fits.gz 2mass-atlas-991214n-h0840209.fits.gz
       5 RA---SIN DEC--SIN    512   1024  73.703293   7.341512  256.50  512.50 -0.00027778  0.00027778    0.00614    1.11748 http://irsa.ipac.caltech.edu/ibe/data/twomass/full/full/000112n/s059/image/hi0590068.fits.gz 2mass-atlas-000112n-h0590068.fits.gz
       6 RA---SIN DEC--SIN    512   1024  73.945072   7.341463  256.50  512.50 -0.00027778  0.00027778    0.01210    1.11081 http://irsa.ipac.caltech.edu/ibe/data/twomass/full/full/991214n/s085/image/hi0850068.fits.gz 2mass-atlas-991214n-h0850068.fits.gz
       7 RA---SIN DEC--SIN    512   1024  73.703264   7.610956  256.50  512.50 -0.00027778  0.00027778    0.00615    1.11748 http://irsa.ipac.caltech.edu/ibe/data/twomass/full/full/000112n/s059/image/hi0590079.fits.gz 2mass-atlas-000112n-h0590079.fits.gz
       8 RA---SIN DEC--SIN    512   1024  73.580859   7.554679  256.50  512.50 -0.00027778  0.00027778    0.01113    1.10958 http://irsa.ipac.caltech.edu/ibe/data/twomass/full/full/991214n/s076/image/hi0760197.fits.gz 2mass-atlas-991214n-h0760197.fits.gz

*/