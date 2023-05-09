package com.github.acteek.burgertermin

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import com.bot4s.telegram.api.declarative._
import com.bot4s.telegram.cats.{Polling, TelegramBot}
import com.bot4s.telegram.methods.{ParseMode, SendMessage}
import com.bot4s.telegram.models.{BotCommand, InlineKeyboardButton, InlineKeyboardMarkup}
import sttp.client3.SttpBackend
import sttp.client3.httpclient.cats.HttpClientCatsBackend

import java.time.format.DateTimeFormatter
import scala.util.{Failure, Success, Try}

class TerminBot(
      token: String
    , backend: SttpBackend[IO, Any]
    , store: SubscriptionStore[IO]
    , sendQ: Queue[IO, Subscription]
) extends TelegramBot[IO](token, backend) with Polling[IO] with Commands[IO] with Logging {

  import TerminBot._

  override def run(): IO[Unit] = for {
    _ <- log.info("Bot has started !")
    _ <- IO.bothOutcome(processNotify(), startPolling())
  } yield ()

  onCommand("start" | "help") { implicit msg =>
    val name     = msg.from.map(_.firstName)
    val response = greeting(name, commands)
    reply(text = response, parseMode = Some(ParseMode.Markdown)).void
  }

  onCommand("/subscribe") { implicit msg =>
    val chatId   = msg.chat.id
    val username = msg.from.fold("")(_.firstName)
    withArgs {
      case Seq(day) =>
        Try(dateTimeFormatter.parse(day)) match {
          case Success(_) =>
            store.put(chatId, day) *>
              reply(s"Subscription for $day created") *>
              log.info(s"$username has made subscription chatId[$chatId] for [$day]")
          case Failure(_) =>
            reply("Wrong date format, please use dd.mm.yyyy") *>
              log.warn(s"Wrong date format for [$day]")

        }

      case _ =>
        store.put(chatId, "All") *>
          reply("Subscription for all days created!") *>
          log.info(s"$username has made subscription chatId[$chatId] for all days")

    }
  }

  onCommand("unsubscribe") { implicit msg =>
    val chatId   = msg.chat.id
    val username = msg.from.fold("NoName")(_.firstName)

    store.delete(msg.chat.id) *>
      reply("Subscription has been canceled") *>
      log.info(s"$username has been canceled subscription chatId[$chatId]")
  }

  private def processNotify(): IO[Unit] = fs2.Stream
    .fromQueueUnterminated(sendQ)
    .evalMapChunk { sub =>
      request(notification(sub))
    }
    .compile
    .drain

}
object TerminBot {
  def resource(token: String, store: SubscriptionStore[IO], sendQ: Queue[IO, Subscription]): Resource[IO, TerminBot] =
    HttpClientCatsBackend
      .resource[IO]()
      .map(backend => new TerminBot(token, backend, store, sendQ))

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.mm.yyyy")
  private val commands = List(
      BotCommand("/subscribe", "Make subscription for termins any days")
    , BotCommand("/subscribe dd.mm.yyyy", "Make subscription for particular day")
    , BotCommand("/unsubscribe", "Delete all subscriptions")
  )

  private def greeting(user: Option[String], commands: List[BotCommand]) =
    s"""|Hello${user.fold("")(u => s", ${u.capitalize}")}!
        |I can notify you of available termins for Berlin Bürgeramt,
        |support commands:
        |${commands.map(c => s"${c.command} - ${c.description}.").mkString("\n")}
        |""".stripMargin

  private def notification(sub: Subscription): SendMessage =
    SendMessage(
        chatId = sub.chatId
      , text = s"""Available termins for last 30 sec.
                  |Please setup a session token first, [here](${TerminService.tokenUrl})
                  |""".stripMargin
      , disableWebPagePreview = Some(true)
      , replyMarkup = Some(
        InlineKeyboardMarkup
          .singleColumn(
            sub.termins.map { term =>
              InlineKeyboardButton.url(term.day, term.ref)
            }
          )
      )
      , parseMode = Some(ParseMode.Markdown)
    )
}
