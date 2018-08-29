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

object Main {

  class HelloWorld extends AbstractHandler {

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
        Mosaic[IO].mosaic(o, radius, b).use { p =>

          IO {
            response.setContentType("application/fits")
            response.setStatus(HttpServletResponse.SC_OK)
            Files.copy(p, response.getOutputStream)
            Console.println("*** DONE")
          }

        }
      }

      action.foreach(_.unsafeRunSync) // todo: error handler
    }

  }

    def main(args: Array[String]): Unit = {
        val server = new Server(8080)
        server.setHandler(new HelloWorld())
        server.start()
        Console.println("Press <Enter> to exit.")
        StdIn.readLine()
        server.stop()
    }

}