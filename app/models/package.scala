import scala.language.experimental.macros
import play.api.libs.json.{Format, Json}
import cn.playscala.mongo.codecs.macrocodecs.JsonFormatMacro

package object models {
  implicit val authorFormat = Json.format[Author]
  implicit def formats[T <: Product]: Format[T] = macro JsonFormatMacro.materializeJsonFormat[T]
}
