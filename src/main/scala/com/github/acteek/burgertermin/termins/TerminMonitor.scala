package com.github.acteek.burgertermin.termins

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.effect.std.Queue
import fs2.Stream
import org.http4s.Method.GET
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.{RequestCookie, Uri}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration.FiniteDuration

trait TerminMonitor[F[_]] {
  def start(timeout: FiniteDuration): IO[Unit]
}

object TerminMonitor {

  implicit val log: Logger[IO] = Slf4jLogger.getLogger[IO]

  def impl(client: Client[IO], q: Queue[IO, UpdateEvent]): IO[TerminMonitor[IO]] =
    Ref
      .of[IO, String]("")
      .map { ref =>
        new TerminMonitor[IO] with Http4sClientDsl[IO] {

          def start(timeout: FiniteDuration): IO[Unit] = for {
            _ <- log.info("Termin monitor has started")
            _ <- setUpToken()
            _ <- Stream
                   .fixedRateStartImmediately[IO](timeout)
                   .evalMap { _ =>
                     getUpdate.attempt
                       .flatMap {
                         case Right(update) =>
                           log
                             .info("Tick updated successfully")
                             .as(update)
                         case Left(err) =>
                           log
                             .warn(s"Update is failed: $err")
                             .flatMap(_ => setUpToken())
                             .as(None)
                       }

                   }
                   .unNone
                   .evalMapChunk(q.offer)
                   .onFinalize(log.info("Termin monitor has ended"))
                   .compile
                   .drain
                   .start
                   .void
          } yield ()

          private def setUpToken(): IO[Unit] =
            for {
              tokenOpt <- client.get(tokenUrl)(res => IO.pure(res.cookies.head.content)).attempt.flatMap {
                            case Right(token) =>
                              log.debug(s"Get new session token [$token]") *>
                                IO.pure(Some(token))
                            case Left(ex) =>
                              log.error(s"Update token is failed, use prev: $ex") *>
                                IO.pure(None)

                          }
              _ <- ref.update(old => tokenOpt.getOrElse(old))
            } yield ()

          private def getUpdate: IO[Option[UpdateEvent]] =
            for {
              token <- ref.get
              url = s"$baseUrl/terminvereinbarung/termin/day/"
              uri <- IO.fromEither(Uri.fromString(url))
              cookie = RequestCookie("Zmsappointment", token)
              req     <- IO.pure(GET(uri).addCookie(cookie))
              payload <- client.expect[String](req)
              update  <- IO(UpdateEvent.parse(payload))
            } yield update

        }

      }
}
