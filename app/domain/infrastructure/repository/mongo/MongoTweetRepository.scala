package domain.infrastructure.repository.mongo

import cn.playscala.mongo.Mongo
import domain.infrastructure.repository.TweetRepository
import javax.inject.Inject
import models.{Message, Tweet}
import play.api.libs.json.Json.obj
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Created by xjpz on 2019/11/8.
  */
class MongoTweetRepository @Inject()(mongo: Mongo) extends TweetRepository {

  /**
    * 新增说说
    */
  def add(tweet: Tweet): Future[Boolean] = {
    mongo.insertOne[Tweet](tweet).map{ _ => true}
  }

  def findById(id: String): Future[Option[Tweet]] = {
    mongo.findById[Tweet](id)
  }

  def count(): Future[Long] = {
    mongo.collection[Tweet].count()
  }

  /**
    * 查询最新说说
    */
  def findList(sort:JsObject, skip: Int, limit: Int):Future[List[Tweet]] ={
    mongo.find[Tweet]().sort(sort).skip(skip).limit(limit).list()
  }

  /**
    * 查询最新说说
    */
  def findLatestList(limit: Int):Future[List[Tweet]] ={
    mongo.find[Tweet]().sort(Json.obj("createTime" -> -1)).limit(limit).list()
  }

  /**
    * 查询热门说说
    */
  def findHotList(limit: Int):Future[List[Tweet]] ={
    mongo.find[Tweet]().sort(Json.obj("voteStat.count" -> -1)).limit(limit).list
  }

  /**
    * 删除
    */
  def delete(id: String) = {
    mongo.deleteMany[Tweet](obj("_id" -> id))
  }

}
