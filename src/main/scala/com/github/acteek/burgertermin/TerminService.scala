package com.github.acteek.burgertermin

import cats.effect.IO
import cats.effect.kernel.Ref
import io.circe.syntax._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import cats.implicits._
import fs2.Stream
import org.http4s.Method.GET
import org.http4s.{RequestCookie, Uri}

import scala.concurrent.duration._

trait TerminService[F[_]] {
  def getTermins: F[List[Termin]]
  def startTerminMonitor(targetDay: String): IO[Unit]
}

object TerminService extends Logging {

  def impl(client: Client[IO]): IO[TerminService[IO]] =
    Ref
      .of[IO, String]("")
      .map { ref =>
        new TerminService[IO] with Http4sClientDsl[IO] {

          private def setUpToken(): IO[Unit] = {
            val burgList = burgerms.mkString(",")
            val url = s"$baseUrl/terminvereinbarung/termin/tag.php?termin=1&anliegen=120686&dienstleisterlist=$burgList"
            for {
              token <- client.get(url)(res => IO.pure(res.cookies.head.content))
              _     <- ref.set(token)
            } yield ()

          }

          private def printConsole(termins: List[Termin]): IO[Unit] =
            if (termins.isEmpty)
              logger.info(s"No available termins")
            else
              logger.info(s"""Available termins:""") *>
                termins.map(_.asJson.noSpaces).traverse_(logger.info(_))

          private def sendReq(url: String): IO[String] = for {
            token <- ref.get
            uri   <- IO.fromEither(Uri.fromString(url))
            cookie = RequestCookie("Zmsappointment", token)
            req     <- IO.pure(GET(uri).addCookie(cookie))
            payload <- client.expect[String](req)

          } yield payload

          def getTermins: IO[List[Termin]] =
            for {
              payload <- sendReq(s"$baseUrl/terminvereinbarung/termin/day/")
              termins <- IO(Termin.parse(payload))
            } yield termins

          def startTerminMonitor(targetDay: String): IO[Unit] = for {
            _ <- setUpToken()
            _ <- Stream
                   .fixedRateStartImmediately[IO](30.seconds)
                   .evalMap { _ =>
                     getTermins.attempt
                       .flatMap {
                         case Right(termins) =>
                           printConsole(termins).as(termins)
                         case Left(err) =>
                           logger.warn(s"Error during update: $err") *>
                             setUpToken().as(List.empty[Termin])
                       }
                       .map(_.find(_.title contains targetDay))
                       .flatMap {
                         case Some(term) =>
                           logger.info(s"Matched for $targetDay - ${term.asJson.noSpaces}")
                         case None =>
                           logger.info(s"Not matched any termins for $targetDay")
                       }
                   }
                   .compile
                   .drain
                   .start
                   .void
          } yield ()

        }

      }
}
