package com.github.acteek.burgertermin

import fs2.Stream
import cats.implicits._
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import com.bot4s.telegram.api.declarative._
import com.bot4s.telegram.cats.{Polling, TelegramBot}
import com.bot4s.telegram.methods.{ParseMode, SendMessage, SetMyCommands}
import com.bot4s.telegram.models.{BotCommand, InlineKeyboardButton, InlineKeyboardMarkup}
import com.github.acteek.burgertermin.termins.UpdateEvent
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.client3.SttpBackend
import sttp.client3.httpclient.cats.HttpClientCatsBackend

class Bot(
      token: String
    , backend: SttpBackend[IO, Any]
    , store: ChatStore[IO]
    , sendQ: Queue[IO, UpdateEvent]
) extends TelegramBot[IO](token, backend) with Polling[IO] with Commands[IO] with Callbacks[IO] {

  import Bot._

  implicit val log: Logger[IO] = Slf4jLogger.getLogger[IO]

  def startPollingSafety(): IO[Unit] =
    log
      .info("Bot polling has started")
      .flatMap(_ => startPolling())
      .attempt
      .flatMap {
        case Right(_) => log.info("Bot has stopped normally")
        case Left(ex) =>
          log
            .warn(s"Bot has stopped with error: $ex")
            .flatMap(_ => startPollingSafety())
      }

  def startProcessUpdates(): IO[Unit] = Stream
    .fromQueueUnterminated(sendQ)
    .evalMapChunk { update =>
      store.getAll.flatMap { chatIds =>
        chatIds.traverse { id =>
          val msg = Bot.notify(id, update)
          request(msg)
        }
      }

    }
    .compile
    .drain
    .start
    .void

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

  onCommand("users") { implicit msg =>
    store.getAll
      .map(_.size)
      .flatMap { count =>
        reply(s"Active users => $count")

      }
      .void

  }

  onCommand("unsubscribe") { implicit msg =>
    val chatId   = msg.chat.id
    val username = msg.from.fold("NoName")(_.firstName)

    store.delete(msg.chat.id) *>
      reply("Subscription has been canceled") *>
      log.info(s"$username has been canceled subscription chatId[$chatId]")
  }

  onCommand("subscribe") { implicit msg =>
    for {
      chatId <- IO.pure(msg.chat.id)
      name   <- IO.pure(msg.from.fold("NoName")(_.firstName))
      _      <- store.put(chatId)
      _      <- reply("You have subscribed for updates")
      _      <- log.info(s"$name has made subscription chatId[$chatId]")
    } yield ()

  }

}

object Bot {
  def resource(token: String, store: ChatStore[IO], sendQ: Queue[IO, UpdateEvent]): Resource[IO, Bot] =
    HttpClientCatsBackend
      .resource[IO]()
      .map(backend => new Bot(token, backend, store, sendQ))

  private val commands = List(
      BotCommand("start", "Main menu")
    , BotCommand("status", "Check subscription status")
    , BotCommand("subscribe", "Make subscription")
    , BotCommand("unsubscribe", "Delete subscription")
  )

  private def greeting(user: Option[String], commands: List[BotCommand]) =
    s"""|Hello${user.fold("")(u => s", ${u.capitalize}")}!
        |I can notify you of available termins for Berlin Bürgeramt,
        |
        |Commands:
        |${commands.map(c => s"/${c.command} - ${c.description}.").mkString("\n")}
        |""".stripMargin

  private def notify(chatId: Long, update: UpdateEvent): SendMessage =
    SendMessage(
        chatId = chatId
      , text = "Some slots are available"
      , disableWebPagePreview = Some(true)
      , replyMarkup = Some(
        InlineKeyboardMarkup
          .singleButton(
            InlineKeyboardButton.url("View", update.ref)
          )
      )
      , parseMode = Some(ParseMode.Markdown)
    )
}
