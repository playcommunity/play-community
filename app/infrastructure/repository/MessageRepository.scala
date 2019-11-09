package infrastructure.repository

import models.Message

import scala.concurrent.Future

/**
 *
 * @author 梦境迷离
 * @since 2019-11-09
 * @version v1.0
 */
trait MessageRepository {

  def countBy(condition: (String, Any)*): Future[Long]

  def findBy(sortBy: (String, Int), count: Int, findBy: (String, Any)*): Future[List[Message]]

}
