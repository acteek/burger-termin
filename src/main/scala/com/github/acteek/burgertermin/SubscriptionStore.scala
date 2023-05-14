package com.github.acteek.burgertermin

import cats.effect.kernel.Resource
import cats.effect.{IO, Ref}
import cats.implicits._
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log

trait SubscriptionStore[F[_]] {
  def put(chatId: Long, sub: String): F[Unit]
  def get(chatId: Long): F[Option[String]]
  def getAll: F[List[(Long, String)]]
  def delete(chatId: Long): F[Unit]
}

object SubscriptionStore extends Logging {

  def memory: IO[SubscriptionStore[IO]] =
    Ref[IO]
      .of(Map.empty[Long, String])
      .map { ref =>
        new SubscriptionStore[IO] {
          def put(chatId: Long, sub: String): IO[Unit] = ref.update(_.updated(chatId, sub))
          def get(chatId: Long): IO[Option[String]]    = ref.get.map(_.get(chatId))
          def delete(chatId: Long): IO[Unit]           = ref.update(_.removed(chatId))
          def getAll: IO[List[(Long, String)]]         = ref.get.map(_.toList)
        }
      }

  def redis(host: String, pass: String): Resource[IO, SubscriptionStore[IO]] =
    Redis[IO]
      .utf8(s"rediss://default:$pass@$host:25061")
      .map { redis =>
        new SubscriptionStore[IO] {
          val pref: String = "sub"

          def put(chatId: Long, sub: String): IO[Unit] = redis.set(s"$pref-$chatId", sub)
          def get(chatId: Long): IO[Option[String]]    = redis.get(s"$pref-$chatId")

          def getAll: IO[List[(Long, String)]] = for {
            keys <- redis.keys(s"$pref-*")
            res <- keys.traverseFilter { key =>
                     val chatId = key.split("-").last.toLong
                     redis.get(key).map(_.map(v => chatId -> v))
                   }

          } yield res

          def delete(chatId: Long): IO[Unit] = redis.del(s"$pref-$chatId").void
        }

      }

  implicit def redisLogs: Log[IO] = new Log[IO] {
    def debug(msg: => String): IO[Unit] = log.debug(msg)
    def error(msg: => String): IO[Unit] = log.error(msg)
    def info(msg: => String): IO[Unit]  = log.info(msg)
  }

}
