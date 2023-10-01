package com.github.acteek.burgertermin

import cats.effect.IO
import cats.effect.kernel.Resource
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.log4cats.log4CatsInstance
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait ChatStore[F[_]] {
  def put(chatId: Long): F[Unit]
  def get(chatId: Long): F[Option[String]]
  def getAll: F[List[Long]]
  def delete(chatId: Long): F[Unit]
}

object ChatStore {
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  def redis(conf: Config.Redis): Resource[IO, ChatStore[IO]] =
    Redis[IO]
      .utf8(s"rediss://default:${conf.pass}@${conf.host}:${conf.port}")
      .map { redis =>
        new ChatStore[IO] {
          val pref: String = "sub"

          def put(chatId: Long): IO[Unit]           = redis.set(s"$pref-$chatId", "All")
          def get(chatId: Long): IO[Option[String]] = redis.get(s"$pref-$chatId")
          def getAll: IO[List[Long]]                = redis.keys(s"$pref-*").map(_.map(_.split("-").last.toLong))
          def delete(chatId: Long): IO[Unit]        = redis.del(s"$pref-$chatId").void
        }

      }

}
