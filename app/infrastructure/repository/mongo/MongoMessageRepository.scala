package infrastructure.repository.mongo

import cn.playscala.mongo.Mongo
import infrastructure.repository.MessageRepository
import javax.inject.{ Inject, Singleton }
import models.Message
import play.api.libs.json.Json
import play.api.libs.json.Json.obj

import scala.concurrent.Future

/**
 *
 * @author 梦境迷离
 * @since 2019-11-09
 * @version v1.0
 */
@Singleton
class MongoMessageRepository @Inject()(mongo: Mongo) extends MessageRepository {

  import utils.ImplicitUtils.ConvertJsValue

  override def countBy(conditions: (String, Any)*) = {
    val tmp = conditions.toSeq.map(x => (x._1, x._2.toJsValue))
    mongo.count[Message](obj(tmp: _*))
  }


  //TODO 类型不安全
  override def findBy[R <: AnyRef](sortBy: (String, Int), count: Int, findBy: (String, String)*): Future[List[Message]] = {
    val tmp = findBy.toSeq.map(x => (x._1, x._2.toJsValue))
    mongo.find[Message](obj(tmp: _*)).sort(obj(sortBy._1 -> sortBy._2.toJsValue)).limit(count).list()
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
