package mosaic

import cats.effect._
import cats.implicits._
import java.nio.file._
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import mosaic.algebra._
import org.eclipse.jetty.server.{ Handler, Request, Server }
import org.eclipse.jetty.server.handler.AbstractHandler
import scala.io.StdIn
import scala.Predef._

object Main extends IOApp {

  /** Our cache lives here. */
  val cacheRoot = Paths.get("/tmp", "mosaic-cache")

  /**
   * Construct an HttpMosaic using the given [per-request] logger and a response that we will use
   * to send the FITS file to the user.
   */
  def ioHttpMosaic(log: Log[IO], res: HttpServletResponse): HttpMosaic[IO] =
    HttpMosaic(
      res,
      HttpMosaic.cache(
        log,
        Temp[IO],
        cacheRoot,
        Mosaic(
          log,
          MontageR(Montage(Exec(log)), Temp[IO]),
          Fetch(log, Cache.urlCache(log, Temp[IO], cacheRoot), Temp[IO])
        )
      )
    )

  /** Parse arguments out of the request, or raise an error. */
  def parse(req: HttpServletRequest): IO[(String, Double, Char)] = {
    val obj    = Option(req.getParameter("object"))
    val radius = Option(req.getParameter("radius")).fold(0.25)(_.toDouble)
    val band   = Option(req.getParameter("band")).map(_.head)
    (obj, band).mapN((_, radius, _)).liftTo[IO] {
      new Exception(s"Invalid query string: ${req.getQueryString}"): Throwable
    }
  }

  /** Our functional servlet handler. */
  def mosaic(req: HttpServletRequest, res: HttpServletResponse): IO[Unit] =
    for {
      log  <- Log.newLog[IO]
      _    <- log.info(s"""${req.getMethod} ${req.getRequestURL}${Option(req.getQueryString).foldMap("?" + _)}""")
      args <- parse(req); (objOrLoc, radius, band) = args
      mos   = ioHttpMosaic(log, res)
      bs   <- mos.respond(objOrLoc, radius, band)
      _    <- log.info(s"Done. Sent $bs bytes.")
    } yield ()

  /** Convert a functional handler into a Jetty handler. */
  def mkHandler(f: (HttpServletRequest, HttpServletResponse) => IO[Unit]): Handler =
    new AbstractHandler {
      override def handle(tar: String, base: Request, req: HttpServletRequest, res: HttpServletResponse) =
        f(req, res).unsafeRunSync
    }

  /** Our entry point. */
  def run(args: List[String]): IO[ExitCode] =
    IO {
      val server = new Server(8080)
      server.setHandler(mkHandler(mosaic))
      server.start()
      Console.println("Press <Enter> to exit.")
      StdIn.readLine()
      server.stop()
      ExitCode.Success
    }

}

