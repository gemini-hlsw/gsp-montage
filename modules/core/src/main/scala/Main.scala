package mosaic

import cats.effect._
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import java.nio.file.Paths
import mosaic.algebra._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.Server
import scala.Predef.{ -> => _, _}

object Main extends IOApp {

  val cacheRoot = Paths.get("/tmp", "mosaic-cache")

  object Object extends QueryParamDecoderMatcher[String]("object")
  object Radius extends OptionalQueryParamDecoderMatcher[Double]("radius")
  object Band   extends QueryParamDecoderMatcher[Char]("band")

  def mosaic(log: Logger[IO], blocker: Blocker): HttpMosaic[IO] =
    HttpMosaic(
      HttpMosaic.cache(
        log,
        Temp[IO],
        cacheRoot,
        Mosaic(
          log,
          MontageR(Montage(Exec(log)), Temp[IO]),
          Fetch(log, Cache.urlCache(log, Temp[IO], cacheRoot), Temp[IO])
        )
      ),
      blocker
    )
  def service(blocker: Blocker): HttpApp[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "v1" / "mosaic" :? Object(o) +& Radius(r) +& Band(b) =>
        Log.newLog[IO].flatMap { log =>
          val s = mosaic(log, blocker).respond(o, r.getOrElse(0.25), b)
          Ok(s, `Content-Type`(MediaType.application.fits))
        }
    } .orNotFound

  def server(port: Int): Resource[IO, Server[IO]] = {
    Blocker[IO].flatMap { blocker =>
      BlazeServerBuilder[IO]
        .bindHttp(port, "localhost")
        .withHttpApp(service(blocker))
        .resource
    }
  }

  def run(args: List[String]): IO[ExitCode] = {
    val port = sys.env.get("PORT").fold(8080)(_.toInt)
    server(port).use { _ => IO.never.as(ExitCode.Success) }
  }

}
