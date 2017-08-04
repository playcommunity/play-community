package services

import java.time.OffsetDateTime
import javax.inject.{Inject, Singleton}

import models.{Author, Event}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection
import models.JsonFormats.eventFormat

@Singleton
class EventService @Inject()(val reactiveMongoApi: ReactiveMongoApi) {
  def eventColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-event"))

  def createResource(actor: Author, resId: String, resType: String, resTitle: String) {
    eventColFuture.map(_.insert(Event(BSONObjectID.generate().stringify, actor, "create", resId, resType, resTitle, OffsetDateTime.now())))
  }

  def updateResource(actor: Author, resId: String, resType: String, resTitle: String) {
    eventColFuture.map(_.insert(Event(BSONObjectID.generate().stringify, actor, "update", resId, resType, resTitle, OffsetDateTime.now())))
  }

  def replyResource(actor: Author, resId: String, resType: String, resTitle: String) {
    eventColFuture.map(_.insert(Event(BSONObjectID.generate().stringify, actor, "reply", resId, resType, resTitle, OffsetDateTime.now())))
  }

  def removeResource(actor: Author, resId: String, resType: String, resTitle: String) {
    eventColFuture.map(_.insert(Event(BSONObjectID.generate().stringify, actor, "remove", resId, resType, resTitle, OffsetDateTime.now())))
  }

  def voteResource(actor: Author, resId: String, resType: String, resTitle: String) {
    eventColFuture.map(_.insert(Event(BSONObjectID.generate().stringify, actor, "vote", resId, resType, resTitle, OffsetDateTime.now())))
  }

  def collectResource(actor: Author, resId: String, resType: String, resTitle: String) {
    eventColFuture.map(_.insert(Event(BSONObjectID.generate().stringify, actor, "collect", resId, resType, resTitle, OffsetDateTime.now())))
  }

  def acceptReply(actor: Author, resId: String, resType: String, resTitle: String) {
    eventColFuture.map(_.insert(Event(BSONObjectID.generate().stringify, actor, "accept", resId, resType, resTitle, OffsetDateTime.now())))
  }

}
