package com.github.acteek.burgertermin

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.effect.std.Queue
import io.circe.syntax._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import cats.implicits._
import fs2.concurrent.Signal
import fs2.{Pipe, Stream}
import org.http4s.Method.GET
import org.http4s.{RequestCookie, Uri}

import scala.concurrent.duration.FiniteDuration

trait TerminService[F[_]] {
  def getTermins: F[List[Termin]]
  def startTerminMonitor(stopWhen: Signal[IO, Boolean], timeout: FiniteDuration): IO[Unit]
}

object TerminService extends Logging {
  private val burgList = burgerms.mkString(",")
  val tokenUrl = s"$baseUrl/terminvereinbarung/termin/tag.php?termin=1&anliegen=120686&dienstleisterlist=$burgList"

  def impl(client: Client[IO], store: SubscriptionStore[IO], q: Queue[IO, Subscription]): IO[TerminService[IO]] =
    Ref
      .of[IO, String]("")
      .map { ref =>
        new TerminService[IO] with Http4sClientDsl[IO] {

          private def setUpToken(): IO[Unit] =
            for {
              tokenOpt <- client.get(tokenUrl)(res => IO.pure(res.cookies.head.content)).attempt.flatMap {
                            case Right(token) =>
                              log.info(s"Get new session token [$token]") *>
                                IO.pure(Some(token))
                            case Left(ex) =>
                              log.error(s"Update token is failed, use prev: $ex") *>
                                IO.pure(None)

                          }
              _ <- ref.update(old => tokenOpt.getOrElse(old))
            } yield ()

          private def printConsole(termins: List[Termin]): IO[Unit] =
            if (termins.isEmpty)
              log.info(s"No available termins")
            else
              log.info(s"""Available termins:""") *>
                termins.map(_.asJson.noSpaces).traverse_(log.info(_))

          private def sendReq(url: String): IO[String] = for {
            token <- ref.get
            uri   <- IO.fromEither(Uri.fromString(url))
            cookie = RequestCookie("Zmsappointment", token)
            req     <- IO.pure(GET(uri).addCookie(cookie))
            payload <- client.expect[String](req)

          } yield payload

          private val process: Pipe[IO, List[Termin], Unit] =
            _.filter(_.nonEmpty)
              .evalMapChunk { termins =>
                for {
                  subs <- store.getAll
                  _ <- subs.traverse_ {
                         case (chatId, "All") =>
                           val subscription = Subscription(chatId, termins)
                           q.offer(subscription)
                         case (chatId, day) =>
                           val subscription = Subscription(chatId, termins.filter(_.day == day))
                           q.offer(subscription)
                       }
                } yield ()
              }

          def getTermins: IO[List[Termin]] =
            for {
              payload <- sendReq(s"$baseUrl/terminvereinbarung/termin/day/")
              termins <- IO(Termin.parse(payload))
            } yield termins

          def startTerminMonitor(stopWhen: Signal[IO, Boolean], timeout: FiniteDuration): IO[Unit] = for {
            _ <- setUpToken()
            _ <- Stream
                   .fixedRateStartImmediately[IO](timeout)
                   .evalMap { _ =>
                     getTermins.attempt
                       .flatMap {
                         case Right(termins) =>
                           printConsole(termins).as(termins)
                         case Left(err) =>
                           log.warn(s"Get termins try is failed: $err") *>
                             setUpToken().as(List.empty[Termin])
                       }

                   }
                   .through(process)
                   .interruptWhen(stopWhen)
                   .compile
                   .drain
                   .start
                   .void
          } yield ()

        }

      }
}
