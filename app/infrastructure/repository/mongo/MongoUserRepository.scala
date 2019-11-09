package infrastructure.repository.mongo

import cn.playscala.mongo.Mongo
import infrastructure.repository.UserRepository
import javax.inject.{ Inject, Singleton }
import models.User
import play.api.libs.json.Json
import play.api.libs.json.Json.obj

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * User资源库
 */
@Singleton
class MongoUserRepository @Inject()(mongo: Mongo) extends UserRepository {

  /**
   * 新增用户
   *
   * @param user
   * @return
   */
  def add(user: User): Future[Boolean] = {
    mongo.insertOne[User](user).map { _ => true }
  }

  /**
   * 根据Id构建领域实体。
   *
   * @param id 实体标识。
   * @return 领域实体。
   */
  def findById(id: String): Future[Option[User]] = {
    mongo.findById[User](id)
  }

  /**
   * 根据登录账号查找用户
   *
   * @param login
   * @return
   */
  def findByLogin(login: String): Future[Option[User]] = {
    mongo.find[User](obj("login" -> login)).first
  }

  /**
   * 查询阅读量最高的资源列表, 为提高查询效率，忽略资源内容。
   *
   * @param count 分页大小
   * @return
   */
  def findActiveList(count: Int): Future[List[User]] = {
    mongo.find[User]().sort(Json.obj("stat.resCount" -> -1)).limit(count).list()
  }

  import utils.ImplicitUtils.ConvertJsValueWrappers

  /**
   * 更新用户，用户信息较多，不具体封装更新的状态
   *
   * @param uid
   * @param fields
   * @return
   */
  def updateUser(uid: String, fields: (String, Any)*) = {
    mongo.updateOne[User](
      obj("_id" -> uid),
      obj(
        "$set" -> obj(fields.toSeq.toWrapper: _ *)
      ))
  }

}
