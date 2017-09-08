package services

import javax.inject.{Inject, Singleton}

import models.DocSetting
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.play.json._

@Singleton
class CommonService @Inject()(reactiveMongoApi: ReactiveMongoApi) {
  private def counterColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-counter"))

  def getNextSequence(name: String): Future[Int] = {
    counterColFuture.flatMap{ counterCol =>
      counterCol.findAndModify(
        Json.obj("_id" -> name),
        counterCol.updateModifier(Json.obj("$inc" -> Json.obj("value" -> 1)), true, true)
      ).map(_.result[JsObject]).map{
        case Some(obj) => (obj \ "value").as[Int]
        case None => 0
      }
    }
  }

}