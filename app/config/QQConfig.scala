package config

import com.typesafe.config.{Config, ConfigFactory}

import scala.util.Try

/**
 * QQ秘钥配置读取
 *
 * @author 梦境迷离
 * @version 1.0, 2019-06-27
 */
object QQConfig {

  val config: Config = ConfigFactory.load("qq-api.conf")

  final lazy val QQ_APPID = Try(config.getString("qq.appid")).getOrElse("shabi")
  final lazy val QQ_APPKEY = Try(config.getString("qq.appkey")).getOrElse("shabi")
  final lazy val QQ_ACCESS_TOKEN = Try(config.getString("qq.access-token")).getOrElse("https://graph.qq.com/oauth2.0/token")
  final lazy val QQ_OPEN_ID = Try(config.getString("qq.openid")).getOrElse("https://graph.qq.com/oauth2.0/me?access_token=")
  final lazy val QQ_USER_INFO = Try(config.getString("qq.user-info")).getOrElse("https://graph.qq.com/user/get_user_info?")

}
