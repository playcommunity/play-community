package domain.infrastructure.repository.mongo

import cn.playscala.mongo.Mongo
import domain.infrastructure.repository.MessageRepository
import javax.inject.{ Inject, Singleton }
import models.Message
import play.api.libs.json.Json.obj
import play.api.libs.json.{ JsObject, Json }

import scala.concurrent.Future

/**
 *
 * @author 梦境迷离
 * @since 2019-11-09
 * @version v1.0
 */
@Singleton
class MongoMessageRepository @Inject()(mongo: Mongo) extends MessageRepository {

  override def countBy(conditions: JsObject): Future[Long] = {
    mongo.count[Message](conditions)
  }

  override def findBy(sortBy: JsObject, count: Int, findBy: JsObject): Future[List[Message]] = {
    mongo.find[Message](findBy).sort(sortBy).limit(count).list()
  }

  /**
   * 读取消息
   *
   * @param uid
   */
  def readMessage(uid: String) = {
    mongo.updateMany[Message](obj("uid" -> uid, "read" -> false),
      obj("$set" -> Json.obj("read" -> true)))
  }

  /**
   * 删除消息
   *
   * @param id  消息
   * @param uid 用户
   * @return
   */
  def deleteMessage(id: String, uid: String) = {
    mongo.deleteMany[Message](obj("id" -> id, "uid" -> uid))
  }

  /**
   * 删除用户的所有消息
   *
   * @param uid
   * @return
   */
  def clearMessage(uid: String) = {
    mongo.deleteMany[Message](obj("uid" -> uid))
  }
}
