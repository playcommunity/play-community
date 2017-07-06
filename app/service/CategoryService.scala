package service

import javax.inject.{Inject, Singleton}

import models.Category
import models.JsonFormats.{categoryFormat}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.play.json._
import reactivemongo.play.json.collection.JSONCollection
import scala.concurrent.Future

@Singleton
class CategoryService @Inject() (val reactiveMongoApi: ReactiveMongoApi) {
  val robotColFuture = reactiveMongoApi.database.map(_.collection[JSONCollection]("common-robot"))

  /***
    * 每个分类节点的id唯一
    * 将新的categoryPath合并至已有的categoryPaths，类似于创建一个多层的目录，中间的目录不存在，也要创建。
    */
  def mergeCategoryPaths(categoryList: List[Category], newNamePaths: List[String]): List[Category] = {
    //计算出已有的路径列表
    val categoryNamePathToIdPathMap = scala.collection.mutable.Map[String, String](getNamePathToIdPathMap(categoryList).toList: _*)
    val existNamePathList = categoryNamePathToIdPathMap.keySet
    //计算需要创建的路径
    val needCreatedNamePathList =
    (newNamePaths.toSet -- existNamePathList).toList.flatMap{ newPath =>
      if(newPath == "/"){
        Nil
      } else {
        val splitPaths = newPath.split("/").tail
        val parentPaths = splitPaths.scanLeft(List(""))( (list, p) => list :+ p).toList.tail.map(_.mkString("/"))
        parentPaths.filter(p => !existNamePathList.contains(p))
      }
    }.distinct.sortBy(_.split("/").size)

    //新增的分类列表
    val newCreatedCategoryList =
      needCreatedNamePathList.map{ namePath =>
        val parentNamePath = getParentPath(namePath)
        val cname = namePath.split("/").last
        val cid = System.nanoTime().toString
        if(parentNamePath == "/"){
          val idPath = s"/${cid}"
          categoryNamePathToIdPathMap(namePath) = idPath
          Category(cid, cname, idPath, "/", 1000, false)
        } else {
          val parentIdPath = categoryNamePathToIdPathMap(parentNamePath)
          val idPath = s"${parentIdPath}/${cid}"
          categoryNamePathToIdPathMap(namePath) = idPath
          Category(cid, cname, idPath, parentIdPath, 1000, false)
        }
      }

    categoryList ++ newCreatedCategoryList
  }

  def getPathAndNameMap(robotId: String): Future[Map[String, String]] = {
    for{
      robotCol <- robotColFuture
      robotOpt <- robotCol.find(Json.obj("_id" -> robotId), Json.obj("categories" -> 1)).one[JsObject]
    } yield {
      robotOpt match {
        case Some(obj) =>
          getIdPathToNamePathMap((obj \ "categories").as[List[Category]])
        case None =>
          Map.empty[String, String]
      }
    }
  }

  def getNamePathToIdPathMap(categoryList: List[Category]): Map[String, String] = {
    val categoryIdToNameMap = categoryList.map(c => c._id -> c.name).toMap[String, String]
    categoryList.map{ category =>
      val namePath = category.path.split("/").map( id => if(id == ""){ "" } else { categoryIdToNameMap(id) }).mkString("/")
      namePath -> category.path
    }.toMap
  }

  def getIdPathToNamePathMap(categoryList: List[Category]): Map[String, String] = {
    val categoryIdToNameMap = categoryList.map(c => c._id -> c.name).toMap[String, String]
    categoryList.map{ category =>
      val namePath = category.path.split("/").map( id => if(id == ""){ "" } else { categoryIdToNameMap(id) }).mkString("/")
      category.path -> namePath
    }.toMap
  }

  def getNamePathToStatusMap(categoryList: List[Category]): Map[String, Boolean] = {
    val categoryIdToNameMap = categoryList.map(c => c._id -> c.name).toMap[String, String]
    categoryList.map{ category =>
      val namePath = category.path.split("/").map( id => if(id == ""){ "" } else { categoryIdToNameMap(id) }).mkString("/")
      namePath -> category.disabled
    }.toMap
  }

  def getParentPath(path: String): String = {
    if(path == "/" || path.count(_ == '/') == 1){
      "/"
    } else {
      path.split("/").dropRight(1).mkString("/")
    }
  }

  def isValidCategory(name: String): Boolean = {
    if(name == null || name.trim == ""){
      false
    } else {
      val str = name.trim.replaceAll("[~|`|!|@|#|$|%|^|&|*|+|=|,|.|?|;|:|'|\"|\\|/|<|>|-]", "")
      if(str.length == name.trim.length){
        true
      } else {
        false
      }
    }
  }

  def isValidCategoryPath(namePath: String): Boolean = {
    if(namePath == null || namePath.trim == "" || !namePath.startsWith("/")){
      false
    } else {
      !namePath.split("/").tail.exists(!isValidCategory(_))
    }
  }

}
