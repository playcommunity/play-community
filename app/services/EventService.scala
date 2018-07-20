package services

import javax.inject.{Inject, Singleton}

import cn.playscala.mongo.Mongo
import models.{Author, Event}
import org.bson.types.ObjectId
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsObject, Json}
import utils.DateTimeUtil

@Singleton
class EventService @Inject()(mongo: Mongo) {

  def createResource(actor: Author, resId: String, resType: String, resTitle: String) {
    mongo.insertOne[Event](Event(ObjectId.get().toHexString, actor, "create", resId, resType, resTitle, DateTimeUtil.now()))
  }

  def updateResource(actor: Author, resId: String, resType: String, resTitle: String) {
    mongo.insertOne[Event](Event(ObjectId.get().toHexString, actor, "update", resId, resType, resTitle, DateTimeUtil.now()))
  }

  def replyResource(actor: Author, resId: String, resType: String, resTitle: String) {
    mongo.insertOne[Event](Event(ObjectId.get().toHexString, actor, "reply", resId, resType, resTitle, DateTimeUtil.now()))
  }

  def removeResource(actor: Author, resId: String, resType: String, resTitle: String) {
    mongo.insertOne[Event](Event(ObjectId.get().toHexString, actor, "remove", resId, resType, resTitle, DateTimeUtil.now()))
  }

  def voteResource(actor: Author, resId: String, resType: String, resTitle: String) {
    mongo.insertOne[Event](Event(ObjectId.get().toHexString, actor, "vote", resId, resType, resTitle, DateTimeUtil.now()))
  }

  def unvoteResource(actor: Author, resId: String, resType: String, resTitle: String) {
    mongo.insertOne[Event](Event(ObjectId.get().toHexString, actor, "unvote", resId, resType, resTitle, DateTimeUtil.now()))
  }

  def collectResource(actor: Author, resId: String, resType: String, resTitle: String) {
    mongo.insertOne[Event](Event(ObjectId.get().toHexString, actor, "collect", resId, resType, resTitle, DateTimeUtil.now()))
  }

  def acceptReply(actor: Author, resId: String, resType: String, resTitle: String) {
    mongo.insertOne[Event](Event(ObjectId.get().toHexString, actor, "accept", resId, resType, resTitle, DateTimeUtil.now()))
  }

}
