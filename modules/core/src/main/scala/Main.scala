package mosaic

import cats.effect._
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import mosaic.algebra._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.Server
import scala.Predef.{ -> => _, _}
import dev.profunktor.redis4cats.algebra.StringCommands
import java.net.URL
import dev.profunktor.redis4cats.domain.RedisCodec
import dev.profunktor.redis4cats.domain.LiveRedisCodec
import dev.profunktor.redis4cats.log4cats._
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.ToByteBufEncoder
import java.nio.ByteBuffer
import io.netty.buffer.ByteBuf
import io.lettuce.core.codec.StringCodec
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.connection.RedisURI
import java.util.concurrent.atomic.AtomicInteger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import dev.profunktor.redis4cats.interpreter.Redis

object Main extends IOApp {

  val logCounter = new AtomicInteger(1)

  // new logger with a counter
  def newLog[F[_]: Sync]: F[Logger[F]] =
    Slf4jLogger.fromName[F]("mosaic").flatMap { logger =>
      Sync[F].delay {
        val t0 = System.currentTimeMillis
        val id = logCounter.getAndIncrement()
        logger.withModifiedString { msg =>
          val t1 = System.currentTimeMillis
          f"$id%05d|${t1 - t0}%5d ms| $msg"
        }
      }
    }

  implicit val ioLogger: Logger[IO] =
    Slf4jLogger.getLoggerFromName("mosaic")

  object Object extends QueryParamDecoderMatcher[String]("object")
  object Radius extends OptionalQueryParamDecoderMatcher[Double]("radius")
  object Band   extends QueryParamDecoderMatcher[Char]("band")

  def mosaic(url: URL, log: Logger[IO], redis: StringCommands[IO, URL, Array[Byte]]): HttpMosaic[IO] =
    HttpMosaic(
      log,
      redis,
      url,
      Mosaic(
        log,
        MontageR(Montage(Exec(log)), Temp[IO]),
        Fetch(log, Temp[IO], redis)
      )
    )

  // Lame. This should be an invariant bifunctor
  def codec: RedisCodec[URL, Array[Byte]] =
    LiveRedisCodec[URL, Array[Byte]] {
      val bytes = ByteArrayCodec.INSTANCE
      val utf8  = StringCodec.UTF8
      new io.lettuce.core.codec.RedisCodec[URL, Array[Byte]] with ToByteBufEncoder[URL, Array[Byte]] {
        def decodeKey(bb: ByteBuffer): URL = new URL(utf8.decodeKey(bb))
        def decodeValue(bb: ByteBuffer): Array[Byte] = bytes.decodeValue(bb)
        def encodeKey(key: URL): ByteBuffer = utf8.encodeKey(key.toExternalForm)
        def encodeKey(key: URL, target: ByteBuf): Unit = utf8.encodeKey(key.toExternalForm(), target)
        def encodeValue(value: Array[Byte]): ByteBuffer = bytes.encodeValue(value)
        def encodeValue(value: Array[Byte], target: ByteBuf): Unit = bytes.encodeValue(value, target)
        def estimateSize(keyOrValue: Object): Int =
          keyOrValue match {
            case u: URL         => u.toExternalForm.getBytes("UTF-8").length
            case a: Array[Byte] => a.length
          }
      }
    }

  def service(redis: StringCommands[IO, URL, Array[Byte]]): HttpApp[IO] =
    HttpRoutes.of[IO] {
      case req @ GET -> Root / "v1" / "mosaic" :? Object(o) +& Radius(r) +& Band(b) =>
        newLog[IO].flatMap { log =>
          val url  = new URL("http://0.0.0.0" + req.uri.renderString)
          val mos  = mosaic(url, log, redis)
          val data = mos.stream(o, r.getOrElse(0.25), b)
          Ok(data, `Content-Type`(MediaType.application.fits))
        }
    } .orNotFound

  def server(port: Int, redisUrl: String)(
    implicit log: dev.profunktor.redis4cats.effect.Log[IO]
  ): Resource[IO, Server[IO]] =
    for {
      uri     <- Resource.liftF(RedisURI.make[IO](redisUrl))
      client  <- RedisClient[IO](uri)
      redis   <- Redis[IO, URL, Array[Byte]](client, codec, uri)
      server  <- BlazeServerBuilder[IO]
        .bindHttp(port, "0.0.0.0") // important to use 0.0.0.0 for Heroku
        .withHttpApp(service(redis))
        .resource
    } yield server


  def run(args: List[String]): IO[ExitCode] = {
    val port = sys.env.get("PORT").fold(8080)(_.toInt)
    val redis = sys.env.getOrElse("REDIS_URL", "redis://localhost")
    server(port, redis).use { _ => IO.never.as(ExitCode.Success) }
  }

}
