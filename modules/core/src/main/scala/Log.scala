package mosaic.server

import cats.effect.Sync
import java.util.concurrent.atomic.AtomicInteger

trait Log[F[_]] {
  def info(s: String): F[Unit]
}

object Log {

  private val logCounter = new AtomicInteger(1)

  private val color: Int => String = {
    import Console._, Predef._
    val cs0 = Array(RED, GREEN, BLUE, YELLOW, MAGENTA, CYAN)
    val cs1 = cs0 ++ cs0.map(BOLD + _)
    n => cs1(n % cs1.length)
  }

  def newLog[F[_]: Sync]: F[Log[F]] =
    Sync[F].delay {
      val t0 = System.currentTimeMillis
      val id = logCounter.getAndIncrement()
      val co = color(id)
      new Log[F] {
        def info(s: String) =
          Sync[F].delay {
            val t1 = System.currentTimeMillis
            Console.println(s"${co}** mosaic $id - ${t1 - t0} ms elapsed - $s${Console.RESET}")
          }
      }
    }

}