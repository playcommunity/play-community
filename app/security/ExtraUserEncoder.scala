package security

import models.User

import scala.concurrent.Future

/**
 *
 * @author 梦境迷离
 * @since 2019-10-02
 * @version v1.0
 */
trait ExtraUserEncoder {

  def findUserAndUpgrade(login: String, password: String): Future[Option[User]]

  def updateUserPassword(uid: String, password: String): Unit

}
