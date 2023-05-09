package com.github.acteek.burgertermin

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

case class Subscription(chatId: Long, termins: List[Termin])
object Subscription {
  implicit val subscriptionEncoder: Encoder[Subscription] = deriveEncoder[Subscription]
}
