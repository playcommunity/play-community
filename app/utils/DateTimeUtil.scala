package utils

import java.time.format.DateTimeFormatter
import java.time.{Duration, Instant, LocalDate, LocalDateTime, OffsetDateTime, ZoneOffset}
import java.util.Locale

import scala.util.Try


/**
  * Created by joymufeng on 2017/7/6.
  */
object DateTimeUtil {
  val datePattern = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.ofHours(8))
  val dateTimePattern = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.ofHours(8))

  def toPrettyString(time: OffsetDateTime): String = {
    val d = Duration.between(time, now())
    // val d = Duration.millis(DateTime.now().getMillis - time.getMillis)
    val days = d.toDays
    val hours = d.toHours
    val minutes = d.toMinutes
    val seconds = d.getSeconds

    if (days > 31){
      time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    } else if (days >= 1){
      s"${days}天前"
    } else if (hours >= 1) {
      s"${hours}小时前"
    } else if (minutes >= 1) {
      s"${minutes}分钟前"
    } else {
      s"${seconds}秒前"
    }
  }

  def toPrettyString(time: Instant): String = {
    val d = Duration.between(time, now())
    // val d = Duration.millis(DateTime.now().getMillis - time.getMillis)
    val days = d.toDays
    val hours = d.toHours
    val minutes = d.toMinutes
    val seconds = d.getSeconds

    if (days > 31){
      datePattern.format(time)
    } else if (days >= 1){
      s"${days}天前"
    } else if (hours >= 1) {
      s"${hours}小时前"
    } else if (minutes >= 1) {
      s"${minutes}分钟前"
    } else {
      "刚刚"
    }
  }

  def toPrettyString(epochMillis: Long): String = {
    toPrettyString(Instant.ofEpochMilli(epochMillis))
  }

  def toString(time: OffsetDateTime): String = {
    dateTimePattern.format(time)
  }

  def toString(time: Instant): String = {
    dateTimePattern.format(time)
  }

  def toString(time: OffsetDateTime, format: String): String = {
    time.format(DateTimeFormatter.ofPattern(format))
  }

  // Must format Instant with TimeZone.
  def toString(time: Instant, format: String): String = {
    DateTimeFormatter.ofPattern(format).withZone(ZoneOffset.ofHours(8)).format(time)
  }

  def toString(epochMillis: Long, format: String): String = {
    val time = OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.ofHours(8))
    toString(time, format)
  }

  def now() : Instant = {
    Instant.now()
  }

  /**
   * Parse from 2007-12-03T10:15:30.00Z
   */
  def parse(str: String): Instant = {
    Instant.parse(str)
  }

  /**
   * https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html
   */
  private val dateFormats = List(
    "yyyy-MM-dd",
    "yyyy-M-dd",
    "yyyy-MM-d",
    "dd/MM/yyyy",
    "dd MMM yyyy",
    "yyyy年MM月dd日"
  ).map{ str => DateTimeFormatter.ofPattern(str, Locale.ENGLISH) }

  def dateStrToInstant(dayStr: String): Option[Instant] = {
    dateFormats.find(fmt => Try(fmt.parse(dayStr)).isSuccess) match {
      case Some(fmt) =>
        Instant.ofEpochMilli(LocalDate.from(fmt.parse(dayStr)).toEpochDay)
        Some(LocalDate.from(fmt.parse(dayStr)).atStartOfDay().toInstant(ZoneOffset.UTC))
      case None => None
    }
  }

  /**
   * https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html
   */
  private val dateTimeFormats = List(
    "yyyy-MM-dd'T'HH:mm:ss'Z'",
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
    "EEE, dd MMM yyyy HH:mm:ss 'GMT'",
    "EEE, dd MMM yyyy HH:mm:ss 'Z'"
  ).map(str => DateTimeFormatter.ofPattern(str, Locale.ENGLISH)) ::: List(DateTimeFormatter.RFC_1123_DATE_TIME, DateTimeFormatter.ISO_OFFSET_DATE_TIME)

  def dateTimeStrToInstant(dateTimeStr: String): Option[Instant] = {
    dateTimeFormats.find(fmt => Try(fmt.parse(dateTimeStr)).isSuccess) match {
      case Some(fmt) =>
        Some(LocalDateTime.from(fmt.parse(dateTimeStr)).toInstant(ZoneOffset.UTC))
      case None => None
    }
  }

}
