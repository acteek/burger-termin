package com.github.acteek.burgertermin

import cats.effect.std.Queue
import cats.effect.{IO, IOApp, Resource}
import fs2.concurrent.SignallingRef
import org.http4s.ember.client.EmberClientBuilder

import scala.concurrent.duration._

object Main extends IOApp.Simple with Logging {
  def run: IO[Unit] =
    (for {
      _         <- Resource.eval(log.info("App is starting..."))
      _         <- Resource.eval(printRuntimeInfo)
      token     <- Resource.eval(getEnvVar("BOT_TOKEN"))
      redisHost <- Resource.eval(getEnvVar("REDIS_HOST"))
      redisPass <- Resource.eval(getEnvVar("REDIS_PASS"))
      client    <- EmberClientBuilder.default[IO].build
      store     <- SubscriptionStore.redis(redisHost, redisPass)
      sendQ     <- Resource.eval(Queue.unbounded[IO, Subscription])
      service   <- Resource.eval(TerminService.impl(client, store, sendQ))
      bot       <- TerminBot.resource(token, store, sendQ)

    } yield (bot, service)).use { case (bot, service) =>
      for {
        signal  <- SignallingRef[IO].of(false)
        _       <- service.startTerminMonitor(signal, 5.minute).start
        running <- bot.run().start
        _       <- running.join.flatMap(_ => signal.set(true) *> log.info("App has stopped"))
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

  private def getEnvVar(key: String): IO[String] =
    IO.fromOption(sys.env.get(key))(new RuntimeException(s"$key didn't set..."))

}
