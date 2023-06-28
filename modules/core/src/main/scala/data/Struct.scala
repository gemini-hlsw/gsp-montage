// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package mosaic.data

import atto._, Atto._
import scala.collection.immutable.Map

/** Data type representing Montage `struct` output, a string-keyed map of strings and numbers. */
final case class Struct(toMap: Map[String, Either[Double, String]]) {

  def get(key: String): Option[Either[Double, String]] =
    toMap.get(key)

}

object Struct {

  /** Parser for Montage `struct` output, of the form `[struct k1=v1, k2=v2, ...]`. */
  val parser: Parser[Struct] = {
    val k   = stringOf1(letter)
    val v   = double || stringLiteral
    val kv  = k ~ (char('=') ~> v)
    val kvs = sepBy(kv, string(", "))
    squareBrackets(string("struct ") ~> kvs).map(ps => Struct(ps.toMap))
  }

  /** Parser a Montage `struct` output, yielding a Struct or an error message. */
  def parse(s: String): Either[String, Struct] =
    parser.parseOnly(s).either

}
