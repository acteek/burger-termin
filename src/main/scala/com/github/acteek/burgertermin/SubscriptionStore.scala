package com.github.acteek.burgertermin

import cats.effect.{IO, Ref}

trait SubscriptionStore[F[_]] {
  def put(chatId: Long, sub: String): F[Unit]
  def get(chatId: Long): F[Option[String]]
  def getAll: F[List[(Long, String)]]
  def delete(chatId: Long): F[Unit]
}

object SubscriptionStore {
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
}
