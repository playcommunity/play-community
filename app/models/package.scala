import scala.language.experimental.macros
import play.api.libs.json.{Format, Json}
import cn.playscala.mongo.codecs.macrocodecs.JsonFormatMacro

package object models {
  implicit val authorFormat = Json.format[Author]
  implicit val voteStatFormat = Json.format[VoteStat]
  implicit val commentFormat = Json.format[Comment]
  implicit val replyFormat = Json.format[Reply]
  implicit val linkFormat = Json.format[Link]
  implicit val siteSettingFormat = Json.format[SiteSetting]

  implicit def formats[T <: Product]: Format[T] = macro JsonFormatMacro.materializeJsonFormat[T]
}
