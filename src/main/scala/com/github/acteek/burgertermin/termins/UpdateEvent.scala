package com.github.acteek.burgertermin.termins

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._

case class UpdateEvent(ref: String)

object UpdateEvent {
  implicit val updateEventEncoder: Encoder[UpdateEvent] = deriveEncoder[UpdateEvent]

  def parse(payload: String): Option[UpdateEvent] = {
    val browser  = JsoupBrowser()
    val document = browser.parseString(payload)
    val slots    = document >> elementList(".buchbar")

    slots.headOption.map(_ => UpdateEvent(tokenUrl))

  }
}
