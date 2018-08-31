package mosaic

import cats.effect._
import cats.implicits._
import java.nio.file._
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import mosaic.algebra._
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler
import scala.io.StdIn
import scala.Predef._

object Main extends IOApp {

  /** Our cache lives here. */
  val cacheRoot = Paths.get("/tmp", "mosaic-cache")

  /** Our interpreter. Looks like a lot of work but there's only one way to construct it. */
  def ioHttpMosaic(log: Log[IO]): HttpMosaic[IO] =
    HttpMosaic(
      Mosaic(
        log,
        MontageR(Montage(Exec(log)), Temp[IO]),
        Fetch(log, Cache.urlCache(log, Temp[IO], cacheRoot), Temp[IO])
      )
    )

  def mosaic(objOrLoc: String, radius: Double, band: Char, req: HttpServletRequest, res: HttpServletResponse): IO[Unit] =
    for {
      log <- Log.newLog[IO]
      mos  = ioHttpMosaic(log)
      _   <- log.info(s"""${req.getMethod} ${req.getRequestURL}${Option(req.getQueryString).foldMap("?" + _)}""")
      bs  <- mos.respond(objOrLoc, radius, band, res)
      _   <- log.info(s"Done. Sent $bs bytes.")
    } yield ()

  // Request handler for Jetty, kind of like a Servlet.
  object Handler extends AbstractHandler {
    override def handle(target: String, baseReq: Request, req: HttpServletRequest, res: HttpServletResponse) = {

      // Decode the req and calculate an IO action to run.
      val obj    = Option(req.getParameter("object"))
      val radius = Option(req.getParameter("radius")).fold(0.25)(_.toDouble)
      val band   = Option(req.getParameter("band")).map(_.head)
      val action = (obj, band).mapN(mosaic(_, radius, _, req, res))

      // Run the action, if any, otherwise user gets a 404
      action.foreach(_.unsafeRunSync)
    }
  }

  def run(args: List[String]): IO[ExitCode] =
    IO {
      val server = new Server(8080)
      server.setHandler(Handler)
      server.start()
      Console.println("Press <Enter> to exit.")
      StdIn.readLine()
      server.stop()
      ExitCode.Success
    }

}