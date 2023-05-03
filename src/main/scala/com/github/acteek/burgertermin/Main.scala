package com.github.acteek.burgertermin

import cats.effect.{IO, IOApp, Resource}
import org.http4s.ember.client.EmberClientBuilder

object Main extends IOApp.Simple {
  def run: IO[Unit] = (for {
    client  <- EmberClientBuilder.default[IO].build
    service <- Resource.eval(TerminService.impl(client))
    _       <- Resource.eval(service.startTerminMonitor("04.05.2023"))

  } yield ()).useForever
}
