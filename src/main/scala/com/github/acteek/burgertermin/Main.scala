package com.github.acteek.burgertermin

import cats.effect.std.Queue
import cats.effect.{IO, IOApp, Resource}
import fs2.concurrent.SignallingRef
import org.http4s.ember.client.EmberClientBuilder

object Main extends IOApp.Simple with Logging {
  def run: IO[Unit] =
    (for {
      _       <- Resource.eval(log.info("App is starting..."))
      _       <- Resource.eval(printRuntimeInfo)
      token   <- Resource.eval(IO.fromOption(sys.env.get("BOT_TOKEN"))(new RuntimeException("BOT_TOKEN don't  set...")))
      client  <- EmberClientBuilder.default[IO].build
      store   <- Resource.eval(SubscriptionStore.memory)
      sendQ   <- Resource.eval(Queue.unbounded[IO, Subscription])
      service <- Resource.eval(TerminService.impl(client, store, sendQ))
      bot     <- TerminBot.resource(token, store, sendQ)

    } yield (bot, service)).use { case (bot, service) =>
      for {
        signal  <- SignallingRef[IO].of(false)
        _       <- service.startTerminMonitor(signal).start
        running <- bot.run().start
        _       <- running.join.flatMap(_ => signal.set(true) *> log.warn("App has stopped"))
      } yield ()

    }

  private def printRuntimeInfo: IO[Unit] =
    for {
      runtime <- IO(Runtime.getRuntime)
      mb            = 1024 * 1024
      maxMemoryInMb = runtime.maxMemory() / mb
      cpu           = runtime.availableProcessors()
      _ <- log.info(s"JVM max memory: $maxMemoryInMb MB")
      _ <- log.info(s"CPU available: $cpu")
    } yield ()
}
