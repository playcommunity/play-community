package services

import java.time.Instant

import javax.inject.{Inject, Singleton}
import cn.playscala.mongo.Mongo
import models.Category

@Singleton
class CategoryService @Inject() (mongo: Mongo) {

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
          Category(cid, cname, "", idPath, "/", 1000, false, None, Instant.now(), Instant.now())
        } else {
          val parentIdPath = categoryNamePathToIdPathMap(parentNamePath)
          val idPath = s"${parentIdPath}/${cid}"
          categoryNamePathToIdPathMap(namePath) = idPath
          Category(cid, cname, "", idPath, parentIdPath, 1000, false, None, Instant.now(), Instant.now())
        }
      }

    categoryList ++ newCreatedCategoryList
  }

  /**
   * 构建分类名称路径和分类Id路径的映射。例如将 /scala/basic 映射到其中文路径 /Scala编程语言/基础知识
   * @param categoryList 待映射的分类列表
   * @return Map[String, String]
   */
  def getNamePathToIdPathMap(categoryList: List[Category]): Map[String, String] = {
    val categoryIdToNameMap = categoryList.map(c => c._id -> c.name).toMap[String, String]
    categoryList.map{ category =>
      val namePath = category.path.split("/").map( id => if(id == ""){ "" } else { categoryIdToNameMap.get(id).getOrElse("-") }).mkString("/")
      namePath -> category.path
    }.toMap
  }

  /**
    * 构建分类Id路径和分类名称路径的映射。例如将中文路径 /Scala编程语言/基础知识 映射到 /scala/basic
    * @param categoryList 待映射的分类列表
    * @return Map[String, String]
    */
  def getIdPathToNamePathMap(categoryList: List[Category]): Map[String, String] = {
    val categoryIdToNameMap = categoryList.map(c => c._id -> c.name).toMap[String, String]
    categoryList.map{ category =>
      val namePath = category.path.split("/").map( id => if(id == ""){ "" } else { categoryIdToNameMap.get(id).getOrElse("-") }).mkString("/")
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

  /**
    * 从分类路径中取出第1层路径
    */
  def getBoardPath(path: String): Option[String] = {
    if (path.startsWith("/") && path != "/") {
      Some(path.split("/").take(2).mkString("/") + "/")
    } else {
      None
    }
  }

  /**
    * 获取父路径，以斜杠结尾
    */
  def getParentPath(path: String): String = {
    if (path.startsWith("/") && path != "/") {
      "/"
    } else {
      path.split("/").dropRight(1).mkString("/") + "/"
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
