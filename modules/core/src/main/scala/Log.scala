package mosaic

import cats.effect.Sync
import cats.implicits._
import java.util.concurrent.atomic.AtomicInteger
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

object Log {

  // A counter to create new logger ids.
  private val logCounter = new AtomicInteger(1)

  /**
   * Construct a new logger with a unique serial number. Messages are prefixed with id and elapsed
   * time since construction, so the intent is that each request runs with a new one.
   */
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

}