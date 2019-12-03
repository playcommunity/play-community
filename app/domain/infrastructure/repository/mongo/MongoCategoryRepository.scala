package domain.infrastructure.repository.mongo

import cn.playscala.mongo.Mongo
import domain.infrastructure.repository.CategoryRepository
import javax.inject.{Inject, Singleton}
import models.Category
import play.api.libs.json.Json

import scala.concurrent.Future

/**
  * Resource资源库
  */
@Singleton
class MongoCategoryRepository @Inject()(mongo: Mongo) extends CategoryRepository {

  /**
    * 查询分类列表
    */
  def findAllList(): Future[List[Category]] = {
    mongo.find[Category]().list()
  }

  /**
    * 查询子分类列表
    */
  def findChildren(parentPath: String): Future[List[Category]] = {
    mongo.find[Category](Json.obj("parentPath" -> parentPath)).list()
  }

  /**
    * 查询子分类列表
    */
  def findAllChildren(path: String): Future[List[Category]] = {
    mongo.find[Category](Json.obj("categoryPath" -> Json.obj("$regex" -> s"^${path}"))).list()
  }

  /**
    * 查询子分类列表
    */
  def findChildren(parentPath: String, disabled: Boolean): Future[List[Category]] = {
    mongo.find[Category](Json.obj("parentPath" -> parentPath, "disabled" -> disabled)).list()
  }

  /**
    * 根据path查询分类
    */
  def findByPath(path: String): Future[Option[Category]] = {
    mongo.find[Category](Json.obj("path" -> path)).first
  }

}
