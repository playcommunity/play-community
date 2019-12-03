package domain.infrastructure.repository

import models.Message
import play.api.libs.json.JsObject

import scala.concurrent.Future

/**
 *
 * @author 梦境迷离
 * @since 2019-11-09
 * @version v1.0
 */
trait MessageRepository {

  def countBy(condition: JsObject): Future[Long]

  def findBy(sortBy: JsObject, count: Int, findBy: JsObject): Future[List[Message]]

}
