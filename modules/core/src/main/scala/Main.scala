package mosaic.server

import cats.effect._
import cats.implicits._
import java.nio.file._
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler
import scala.io.StdIn
import scala.Predef._

object Main extends IOApp {

  def streamResponse(path: Path, res: HttpServletResponse): IO[Long] =
    for {
      _ <- IO(res.setContentLength(path.toFile.length.toInt))
      n <- IO(Files.copy(path, res.getOutputStream))
    } yield n

  def mosaic(objOrLoc: String, radius: Double, band: Char, req: HttpServletRequest, res: HttpServletResponse): IO[Unit] =
    for {
      log <- Log.newLog[IO]
      _   <- log.info(s"""${req.getMethod} ${req.getRequestURL}${Option(req.getQueryString).foldMap("?" + _)}""")
      _   <- IO(res.setContentType("application/fits"))
      _   <- IO(res.setStatus(HttpServletResponse.SC_OK))
      mo  =  Mosaic[IO](log, Montage(log), Cache(log, Paths.get("/tmp", "mosaic-cache")))
      bs  <- mo.mosaic(objOrLoc, radius, band).use(streamResponse(_, res: HttpServletResponse))
      _   <- log.info(s"Done. Sent $bs bytes.")
    } yield ()

  object Handler extends AbstractHandler {

    override def handle(
      target: String,
      baseRequest: Request,
      request: HttpServletRequest,
      response: HttpServletResponse
    ) = {

      val obj    = Option(request.getParameter("object"))
      val radius = Option(request.getParameter("radius")).fold(0.25)(_.toDouble)
      val band   = Option(request.getParameter("band")).map(_.head)

      val action = (obj, band).mapN { case (o, b) =>
        mosaic(o, radius, b, request, response)
      }

      try {
        action.foreach(_.unsafeRunSync) // todo: error handler
        Console.println("----")
      } catch {
        case e: Exception =>
          e.printStackTrace
      }
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