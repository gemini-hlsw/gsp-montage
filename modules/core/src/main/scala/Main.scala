package mosaic

import cats.effect._
import cats.implicits._
import java.nio.file._
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import mosaic.algebra._
import org.eclipse.jetty.server.{ Request, Server }
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

  /** Parse arguments out of the request, if possible. */
  def parse(req: HttpServletRequest): Option[(String, Double, Char)] = {
    val obj    = Option(req.getParameter("object"))
    val radius = Option(req.getParameter("radius")).fold(0.25)(_.toDouble)
    val band   = Option(req.getParameter("band")).map(_.head)
    (obj, band).mapN((_, radius, _))
  }

  /** Our mosaic handler. */
  def mosaic(log: Log[IO], req: HttpServletRequest, res: HttpServletResponse): IO[Unit] =
    parse(req) match {

      case Some((objOrLoc, radius, band)) =>
        for {
          bs <- ioHttpMosaic(log, res).respond(objOrLoc, radius, band)
          _  <- log.info(s"Sent $bs bytes.")
        } yield ()

      case None =>
        IO {
          res.sendError(
            HttpServletResponse.SC_BAD_REQUEST,
            s"""|
                |Invalid query string: ${req.getQueryString}
                |Expected the following query arguments:
                |  object - an RA/DEC string like 04:55:10.305 07:55:25.43
                |  radius - a radius in degrees (optional, default 0.25)
                |  band   - a 2MASS band, one of J H K
            """.stripMargin
          )
        }
    }

  /** A very minimal request router. We don't even look at the URI. */
  def route(req: HttpServletRequest, res: HttpServletResponse): IO[Unit] =
    for {
      log <- Log.newLog[IO]
      _   <- log.info(s"""${req.getMethod} ${req.getRequestURL}${Option(req.getQueryString).foldMap("?" + _)}""")
      _   <- req.getMethod match {
               case "GET" => mosaic(log, req, res)
               case _     => IO(res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED))
             }
      _   <- log.info(s"Request complete.")
    } yield ()

  /** Our entry point. */
  def run(args: List[String]): IO[ExitCode] =
    IO {
      val heroku = sys.env.contains("HEROKU")
      val port   = sys.env.get("PORT").fold(8080)(_.toInt)
      val server = new Server(port)
      server.setHandler {
        new AbstractHandler {
          override def handle(
            target:      String,
            baseRequest: Request,
            request:     HttpServletRequest,
            response:    HttpServletResponse
          ): Unit =
            route(request, response).unsafeRunSync
        }
      }
      server.start()
      if (heroku) {
        Console.println("Up and running. Waiting here forever ...")
        server.join()
      } else {
        StdIn.readLine("Up and running. Press <Enter> to shut down the server: ")
        server.stop()
      }
      ExitCode.Success
    }

}

