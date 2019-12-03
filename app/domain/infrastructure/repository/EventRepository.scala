package domain.infrastructure.repository

import models.Event
import play.api.libs.json.JsObject

import scala.concurrent.Future

/**
 *
 * @author 梦境迷离
 * @since 2019-11-10
 * @version v1.0
 */
trait EventRepository {

  def countBy(countBys: JsObject): Future[Long]

  def findBy(sortBy: JsObject, count: Int, findBy: JsObject): Future[List[Event]]

}


