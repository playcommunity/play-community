package utils

import play.api.libs.json.{JsValue, Json}

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
   * 解析accessToken
   *
   * @param body
   * @return
   */
  def getString(body: String): String = {
    val json = body.substring(body.indexOf("=") + 1, body.indexOf("&")).trim
    json
  }
}