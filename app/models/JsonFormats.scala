package models

import play.api.libs.json.Json

/**
  * Created by Le'novo on 2017/7/2.
  */
object JsonFormats {

  // Common
  implicit val linkFormat = Json.format[Link]
  implicit val questionTimeStatFormat = Json.format[QuestionTimeStat]
  implicit val articleTimeStatFormat = Json.format[ArticleTimeStat]
  implicit val userStatFormat = Json.format[UserStat]
  implicit val userSettingFormat = Json.format[UserSetting]
  implicit val categoryFormat = Json.format[Category]
  implicit val viewStatFormat = Json.format[ViewStat]
  implicit val voteStatFormat = Json.format[VoteStat]
  implicit val replyStatFormat = Json.format[ReplyStat]
  implicit val collectStatFormat = Json.format[CollectStat]
  implicit val authorFormat = Json.format[Author]
  implicit val commentFormat = Json.format[Comment]
  implicit val replyFormat = Json.format[Reply]
  implicit val documentFormat = Json.format[Document]
  implicit val articleFormat = Json.format[Article]
  implicit val questionFormat = Json.format[Question]
  implicit val ipLocationFormat = Json.format[IPLocation]
  implicit val userFormat = Json.format[User]
  implicit val messageFormat = Json.format[Message]

  // Stat
  implicit val statCollectFormat = Json.format[StatCollect]
  implicit val statTrafficFormat = Json.format[StatTraffic]
  implicit val statIPFormat = Json.format[StatIP]
  implicit val statVisitorFormat = Json.format[StatVisitor]

  // Search
  implicit val indexedDocumentFormat = Json.format[IndexedDocument]
  implicit val siteSettingFormat = Json.format[SiteSetting]



}
