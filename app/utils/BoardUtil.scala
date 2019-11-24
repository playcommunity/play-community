package utils

import models.Category

object BoardUtil {

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
   * 获取path所有层级路径。例如对于/a/b/c的所有层级路径为List(/, /a/, /a/b/, /a/b/c/)
   */
  def getAllLevelPaths(path: String): List[String] = {
    path.split("/").filter(_ != "").foldLeft(List("/")){ (list, id) => list ::: List(list.last + id + "/")}
  }

  def getChildren(path: String, categoryList: List[Category]): List[Category] = {
    categoryList.filter(_.parentPath == path)
  }

  def getSiblings(path: String, categoryList: List[Category]): List[Category] = {
    path match {
      case "/" => Nil
      case _ =>
        categoryList.find(_.path == path) match {
          case Some(c) => getChildren(c.parentPath, categoryList)
          case None => Nil
        }
    }
  }

}
