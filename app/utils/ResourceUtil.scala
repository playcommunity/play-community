package utils

import java.time.format.DateTimeFormatter
import java.time.{Duration, Instant, OffsetDateTime, ZoneOffset}


/**
  * Created by joymufeng on 2017/7/6.
  */
object ResourceUtil {
  def toPrettyString(resType: String): String = {
    resType match {
      case "doc" => "文档"
      case "article" => "分享"
      case "qa" => "问答"
      case _ => "其它"
    }
  }
}
