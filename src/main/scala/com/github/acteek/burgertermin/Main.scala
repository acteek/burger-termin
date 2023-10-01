package com.github.acteek.burgertermin

import cats.effect.std.Queue
import cats.effect.{IO, IOApp, Resource}
import com.github.acteek.burgertermin.termins.{UpdateEvent, TerminMonitor}
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger


object Main extends IOApp.Simple {
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  def run: IO[Unit] =
    (for {
      _       <- Resource.eval(logger.info("App is starting..."))
      config  <- Resource.eval(Config.load)
      client  <- EmberClientBuilder.default[IO].build
      store   <- ChatStore.redis(config.redis)
      sendQ   <- Resource.eval(Queue.unbounded[IO, UpdateEvent])
      service <- Resource.eval(TerminMonitor.impl(client, sendQ))
      bot     <- Bot.resource(config.telegram.token, store, sendQ)

    } yield (bot, service, config)).use { case (bot, service, config) =>
      for {
        _ <- service.start(config.updatePeriod)
        _ <- bot.startProcessUpdates()
        _ <- bot.startPollingSafety()
      } yield ()

    }

}
