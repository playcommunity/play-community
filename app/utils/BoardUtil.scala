package utils

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

}
