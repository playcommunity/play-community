package infrastructure.repository.mongo

import cn.playscala.mongo.Mongo
import infrastructure.repository.{CategoryRepository, UserRepository}
import javax.inject.{Inject, Singleton}
import models.{Category, Resource, User}
import play.api.libs.json.Json

import scala.concurrent.Future

/**
  * User资源库
  */
@Singleton
class MongoUserRepository @Inject()(mongo: Mongo) extends UserRepository {

  /**
    * 根据Id构建领域实体。
    * @param id 实体标识。
    * @return 领域实体。
    */
  def findById(id: String): Future[Option[User]] = {
    mongo.findById[User](id)
  }

}
