package utils

import java.time.format.DateTimeFormatter
import java.time.{Duration, Instant, OffsetDateTime, ZoneOffset}


/**
  * Created by joymufeng on 2017/7/6.
  */
object DateTimeUtil {
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

  def toPrettyString(epochMillis: Long): String = {
    val time = OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.ofHours(8))
    toPrettyString(time)
  }

  def toString(time: OffsetDateTime, format: String): String = {
    time.format(DateTimeFormatter.ofPattern(format))
  }

  def toString(epochMillis: Long, format: String): String = {
    val time = OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.ofHours(8))
    toString(time, format)
  }

  def now() : OffsetDateTime = {
    OffsetDateTime.now(ZoneOffset.ofHours(8))
  }


}
