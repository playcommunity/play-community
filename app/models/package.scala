
import play.api.libs.json.Json

package object models {

  implicit val authorFormat = Json.format[Author]

}
