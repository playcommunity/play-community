package utils

import org.joda.time.{DateTime, Duration, Interval, Period}

/**
  * Created by Le'novo on 2017/7/6.
  */
object DateTimeUtil {
  def toPrettyString(time: DateTime): String = {
    val d = Duration.millis(DateTime.now().getMillis - time.getMillis)
    val days = d.getStandardDays
    val hours = d.getStandardHours
    val minutes = d.getStandardMinutes
    val seconds = d.getStandardSeconds

    if (days > 31){
      time.toString("yyyy-MM-dd")
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
}
