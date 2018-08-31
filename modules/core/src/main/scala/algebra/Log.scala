package mosaic.algebra

import cats.effect.Sync
import java.util.concurrent.atomic.AtomicInteger

/** An algebra for logging. */
trait Log[F[_]] {

  /** Log a message, somehow. */
  def info(msg: String): F[Unit]

}

object Log {

  // A counter to create new logger ids.
  private val logCounter = new AtomicInteger(1)

  // A function from Int to ANSI color string via a rotating palette.
  private val color: Int => String = {
    import Console._, Predef._
    val cs0 = Array(RED, GREEN, BLUE, YELLOW, MAGENTA, CYAN)
    val cs1 = cs0 ++ cs0.map(BOLD + _)
    n => cs1(n % cs1.length)
  }

  /**
   * Construct a new logger with a unique serial number that will log messages in the "next" color
   * from a rotating palette. Messages are prefixed with id and elapsed time since construction, so
   * the intent is that each requet runs with a new one.
   */
  def newLog[F[_]: Sync]: F[Log[F]] =
    Sync[F].delay {
      val t0 = System.currentTimeMillis
      val id = logCounter.getAndIncrement()
      val co = color(id)
      new Log[F] {
        def info(msg: String) =
          Sync[F].delay {
            val t1 = System.currentTimeMillis
            Console.println(f"${co}$id%05d|${t1 - t0}%5d ms| $msg${Console.RESET}")
          }
      }
    }

}