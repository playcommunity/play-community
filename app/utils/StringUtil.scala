package utils

import play.api.libs.json.{JsValue, Json}

import scala.collection.mutable.HashMap

/**
 * @author 梦境迷离
 * @version 1.0, 2019-07-02
 */
object StringUtil {


  /**
   * 解析openid
   *
   * @param body
   * @return
   */
  def getJsonBody(body: String): JsValue = {
    val json = body.substring(body.indexOf("(") + 1, body.indexOf(")")).trim
    val jsonValue = Json.parse(json)
    jsonValue
  }

  /**
   * 从查询参数中解析 accessToken
   * 还可以解析出 refresh_token
   *
   * @param body
   * @return
   */
  def getString(body: String, key: String = "access_token"): String = {
    val map = HashMap[String, String]() //可变
    if (body.isEmpty || !body.contains("&") && !body.contains("=")) ""
    else {
      val params = body.split("&")
      for (p <- params) {
        val kv = p.split("=")
        if (kv.length == 2) {
          map.+=(kv(0) -> kv(1))
        }
      }
      map.getOrElse(key, "")
    }
  }
}
