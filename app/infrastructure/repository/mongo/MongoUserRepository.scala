package infrastructure.repository.mongo

import cn.playscala.mongo.Mongo
import infrastructure.repository.{CategoryRepository, UserRepository}
import javax.inject.{Inject, Singleton}
import models.{Category, Resource, User}
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
    */
  def add(user: User): Future[Boolean] = {
    mongo.insertOne[User](user).map{ _ => true}
  }

  /**
    * 根据Id构建领域实体。
    * @param id 实体标识。
    * @return 领域实体。
    */
  def findById(id: String): Future[Option[User]] = {
    mongo.findById[User](id)
  }

  /**
    * 查询阅读量最高的资源列表, 为提高查询效率，忽略资源内容。
    */
  def findActiveList(count: Int): Future[List[User]] = {
    mongo.find[User]().sort(Json.obj("stat.resCount" -> -1)).limit(count).list()
  }

  /**
    * 查询渠道用户。
    * @param id 特定渠道用户标识。
    * @return 用户实例。
    */
  def findByChannelId(id: String): Future[Option[User]] = {
    mongo.find[User](obj("channels.id"-> id)).first
  }

}
