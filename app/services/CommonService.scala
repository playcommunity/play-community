package services

import javax.inject.{Inject, Singleton}
import cn.playscala.mongo.Mongo
import com.mongodb.client.model.FindOneAndUpdateOptions
import play.api.libs.json.{JsObject, Json}
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext

@Singleton
class CommonService @Inject()(mongo: Mongo) {
  val counterCol = mongo.collection("common-counter")

  def getNextSequence(name: String): Future[Int] = {
    counterCol.findOneAndUpdate(
      Json.obj("_id" -> name),
      Json.obj("$inc" -> Json.obj("value" -> 1)),
      new FindOneAndUpdateOptions().upsert(true)
    ).map{ obj =>
      (obj \ "value").as[Int]
    }
  }
}