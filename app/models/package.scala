
import play.api.libs.json.Json

package object models {

  implicit val voteStatFormat = Json.format[VoteStat]
  implicit val authorFormat = Json.format[Author]

}
