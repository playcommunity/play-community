package utils

import java.time.format.DateTimeFormatter
import java.time.{Duration, Instant, OffsetDateTime, ZoneOffset}


/**
  * Created by joymufeng on 2017/7/6.
  */
object AppUtil {
  def prettyResource(resType: String): String = {
    resType match {
      case "doc" => "文档"
      case "article" => "分享"
      case "qa" => "问答"
      case "tweet" => "说说"
      case _ => "其它"
    }
  }

  def prettyAction(action: String): String = {
    action match {
      case "create" => "创建"
      case "update" => "更新"
      case "reply" => "回复"
      case "remove" => "删除"
      case "vote" => "点赞"
      case "collect" => "收藏"
      case "accept" => "采纳回复"
      case _ => "其它"
    }
  }
}
