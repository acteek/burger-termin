package com.github.acteek.burgertermin

import cats.effect.{IO, Outcome}
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import com.bot4s.telegram.api.declarative._
import com.bot4s.telegram.cats.{Polling, TelegramBot}
import com.bot4s.telegram.methods.{DeleteMessage, EditMessageReplyMarkup, ParseMode, SendMessage, SetMyCommands}
import com.bot4s.telegram.models.{BotCommand, InlineKeyboardButton, InlineKeyboardMarkup}
import fs2.concurrent.{Signal, SignallingRef}
import sttp.client3.SttpBackend
import sttp.client3.httpclient.cats.HttpClientCatsBackend


class TerminBot(
      token: String
    , backend: SttpBackend[IO, Any]
    , store: SubscriptionStore[IO]
    , sendQ: Queue[IO, Subscription]
) extends TelegramBot[IO](token, backend) with Polling[IO] with Commands[IO] with Callbacks[IO] {

  import TerminBot._

  override def run(): IO[Unit] = for {
    signal <- SignallingRef[IO].of(false)
    _      <- processNotify(signal).start
    poll   <- startPolling().start
    _      <- log.info("Bot has started")
    _ <- poll.join.flatMap {
           case Outcome.Errored(_) => startPolling().start
           case _                  => signal.set(true)
         }
    _ <- log.info("Bot has stopped")
  } yield ()

  onCommand("start") { implicit msg =>
    val name     = msg.from.map(_.firstName)
    val response = greeting(name, commands)

    request(SetMyCommands(commands)) *>
      reply(text = response, parseMode = Some(ParseMode.Markdown)).void
  }

  onCommand("status") { implicit msg =>
    store
      .get(msg.chat.id)
      .flatMap {
        case Some(sub) => reply(s"You have active subscription for $sub")
        case None      => reply(s"You don't have active subscription")
      }
      .void
  }

  onCommand("subscribe") { implicit msg =>
    val keyboard = Utils.buildDaysKeys(offset = 0)
    reply("Pick a day", replyMarkup = Some(keyboard)).void
  }

  onCommand("unsubscribe") { implicit msg =>
    val chatId   = msg.chat.id
    val username = msg.from.fold("NoName")(_.firstName)

    store.delete(msg.chat.id) *>
      reply("Subscription has been canceled") *>
      log.info(s"$username has been canceled subscription chatId[$chatId]")
  }

  onCallbackWithTag("subscribe_") { implicit cbq =>
    for {
      date   <- IO.pure(cbq.data.fold("All")(_.trim))
      chatId <- IO.pure(cbq.message.map(_.source).getOrElse(0L))
      msgId  <- IO.pure(cbq.message.map(_.messageId).getOrElse(0))
      _      <- store.put(chatId, date)
      _      <- log.info(s"${cbq.from.firstName} has made subscription chatId[$chatId] for [$date]")
      _      <- ackCallback(Some("Update successfully!"))
      _      <- request(DeleteMessage(chatId = chatId, messageId = msgId))
      _      <- request(SendMessage(chatId = chatId, text = s"You have subscribed for $date"))
    } yield ()

  }

  onCallbackWithTag("month_") { implicit cbq =>
    for {
      offset <- IO.pure(cbq.data.fold(0)(_.toInt))
      chatId <- IO.pure(cbq.message.map(_.source).getOrElse(0L))
      msgId  <- IO.pure(cbq.message.map(_.messageId).getOrElse(0))
      keyboard = Utils.buildDaysKeys(offset)
      _ <- request(
             EditMessageReplyMarkup(
                 chatId = Some(chatId)
               , messageId = Some(msgId)
               , replyMarkup = Some(keyboard)
             )
           )
    } yield ()

  }

  private def processNotify(stopWhen: Signal[IO, Boolean]): IO[Unit] = fs2.Stream
    .fromQueueUnterminated(sendQ)
    .evalMapChunk { sub =>
      request(notification(sub))
    }
    .interruptWhen(stopWhen)
    .compile
    .drain

}

object TerminBot {
  def resource(token: String, store: SubscriptionStore[IO], sendQ: Queue[IO, Subscription]): Resource[IO, TerminBot] =
    HttpClientCatsBackend
      .resource[IO]()
      .map(backend => new TerminBot(token, backend, store, sendQ))

  private val commands = List(
      BotCommand("start", "Main menu")
    , BotCommand("subscribe", "Make subscription")
    , BotCommand("status", "Get active subscription")
    , BotCommand("unsubscribe", "Delete subscription")
  )

  private def greeting(user: Option[String], commands: List[BotCommand]) =
    s"""|Hello${user.fold("")(u => s", ${u.capitalize}")}!
        |I can notify you of available termins for Berlin Bürgeramt,
        |
        |Commands:
        |${commands.map(c => s"/${c.command} - ${c.description}.").mkString("\n")}
        |""".stripMargin

  private def notification(sub: Subscription): SendMessage =
    SendMessage(
        chatId = sub.chatId
      , text = s"""Available termins for last 1 min.
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
