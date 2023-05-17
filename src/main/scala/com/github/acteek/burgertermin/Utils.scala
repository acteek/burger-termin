package com.github.acteek.burgertermin

import com.bot4s.telegram.models.{InlineKeyboardButton, InlineKeyboardMarkup}

import java.time._
import java.time.format.DateTimeFormatter
import scala.collection.mutable.ListBuffer

object Utils {

  private val timeZone                   = ZoneId.of("Europe/Berlin")
  val formatter: DateTimeFormatter       = DateTimeFormatter.ofPattern("dd.MM.yyyy")
  val buttonFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

  def monthWorkDays(monthOffset: Int): List[LocalDate] = {
    val date     = LocalDate.now(timeZone)
    val month    = date.plusMonths(monthOffset.toLong).getMonth
    val firstDay = if (monthOffset == 0) date.getDayOfMonth else 1

    val ym                    = YearMonth.of(date.getYear, month)
    val firstOfMonth          = ym.atDay(firstDay)
    val firstOfFollowingMonth = ym.plusMonths(1).atDay(1)

    val acc = ListBuffer.empty[LocalDate]

    firstOfMonth
      .datesUntil(firstOfFollowingMonth)
      .filter(d => d.getDayOfWeek != DayOfWeek.SATURDAY && d.getDayOfWeek != DayOfWeek.SUNDAY)
      .forEach(d => acc.addOne(d))

    acc.result()
  }

  def buildDaysKeys(offset: Int): InlineKeyboardMarkup = {
    val days = Utils.monthWorkDays(offset)
    val calendar = InlineKeyboardMarkup(
      days
        .sliding(5, 5)
        .map { row =>
          row.map { date =>
            InlineKeyboardButton(
                text = date.format(buttonFormatter)
              , callbackData = Some(s"subscribe_${date.format(Utils.formatter)}")
            )
          }

        }
        .toList
    )

    val next = Seq(
        InlineKeyboardButton(
          text = ">>"
        , callbackData = Some(s"month_1")
      )
      , InlineKeyboardButton(
          text = "All"
        , callbackData = Some(s"subscribe_All")
      )
    )

    val prev = Seq(
        InlineKeyboardButton(
          text = "<<"
        , callbackData = Some(s"month_0")
      )
      , InlineKeyboardButton(
          text = "All"
        , callbackData = Some(s"subscribe_All")
      )
    )

    if (offset == 0)
      calendar.copy(inlineKeyboard = calendar.inlineKeyboard ++ Seq(next))
    else if (offset == 1)
      calendar.copy(inlineKeyboard = calendar.inlineKeyboard ++ Seq(prev))
    else calendar

  }

}
