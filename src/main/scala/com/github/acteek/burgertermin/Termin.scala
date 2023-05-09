package com.github.acteek.burgertermin

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import cats.implicits._

case class Termin(day: String, ref: String)
object Termin {
  implicit val terminEncoder: Encoder[Termin] = deriveEncoder[Termin]

  def parse(payload: String): List[Termin] = {
    val browser  = JsoupBrowser()
    val document = browser.parseString(payload)
    val slots    = document >> elementList(".buchbar")
    slots
      .map { el =>
        val title = el >?> attr("title")("a")
        val href  = el >?> attr("href")("a")

        (title, href).mapN { case (title, ref) =>
          val day = title.split(" ").head
          Termin(day, s"$baseUrl$ref")
        }

      }
      .collect { case Some(termin) => termin }

  }

  def pickBurger(termin: Termin, payload: String): Option[Termin] = {
    val browser  = JsoupBrowser()
    val document = browser.parseString(payload)
    val burgers  = document >> elementList(".frei")
    burgers
      .map(_ >?> attr("href")("a"))
      .collectFirst { case Some(href) => termin.copy(ref = s"$baseUrl$href") }
  }

}
