import scala.language.experimental.macros
import play.api.libs.json.{Format}
import cn.playscala.mongo.codecs.macrocodecs.JsonFormatMacro

package object models {
  implicit def formats[T <: Product]: Format[T] = macro JsonFormatMacro.materializeJsonFormat[T]
}
