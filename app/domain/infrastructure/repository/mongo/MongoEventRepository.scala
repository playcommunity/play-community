package domain.infrastructure.repository.mongo

import cn.playscala.mongo.Mongo
import domain.infrastructure.repository.EventRepository
import javax.inject.{ Inject, Singleton }
import models.Event
import play.api.libs.json.JsObject

import scala.concurrent.Future

/**
 *
 * @author 梦境迷离
 * @since 2019-11-10
 * @version v1.0
 */
@Singleton
class MongoEventRepository @Inject()(mongo: Mongo) extends EventRepository {

  override def countBy(countBys: JsObject): Future[Long] = {
    mongo.collection[Event].count(countBys)
  }

  override def findBy(sortBy: JsObject, count: Int, findBy: JsObject): Future[List[Event]] = {
    mongo.find[Event](findBy).sort(sortBy).limit(count).list
  }
}
