package models

import play.api.libs.json.Json

/**
  * Created by Le'novo on 2017/7/2.
  */
object JsonFormats {

  implicit val articleTimeStatFormat = Json.format[ArticleTimeStat]
  implicit val categoryFormat = Json.format[Category]
  implicit val viewStatFormat = Json.format[ViewStat]
  implicit val voteStatFormat = Json.format[VoteStat]
  implicit val authorFormat = Json.format[Author]
  implicit val commentFormat = Json.format[Comment]
  implicit val replyFormat = Json.format[Reply]
  implicit val documentFormat = Json.format[Document]
  implicit val articleFormat = Json.format[Article]
  implicit val questionFormat = Json.format[Question]

}
