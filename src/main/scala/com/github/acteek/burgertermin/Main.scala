package com.github.acteek.burgertermin

import cats.effect.std.Queue
import cats.effect.{IO, IOApp, Resource}
import org.http4s.ember.client.EmberClientBuilder

object Main extends IOApp.Simple {
  def run: IO[Unit] = (for {
    token   <- Resource.eval(IO(sys.env.getOrElse("BOT_TOKEN", "5678047616:AAGks3xy0_JRkmwxuCZjCNJEDHqVAfwzveY")))
    client  <- EmberClientBuilder.default[IO].build
    store   <- Resource.eval(SubscriptionStore.memory)
    sendQ   <- Resource.eval(Queue.unbounded[IO, Subscription])
    service <- Resource.eval(TerminService.impl(client, store, sendQ))
    bot     <- TerminBot.resource(token, store, sendQ)
    _       <- Resource.eval(IO.bothOutcome(bot.run(), service.startTerminMonitor()))

  } yield ()).useForever
}
