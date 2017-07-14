package models

import play.api.libs.json.Json

/**
  * Created by Le'novo on 2017/7/2.
  */
object JsonFormats {

  // Common
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
  implicit val userFormat = Json.format[User]
  implicit val messageFormat = Json.format[Message]

  // Stat
  implicit val statCollectFormat = Json.format[StatCollect]


}
