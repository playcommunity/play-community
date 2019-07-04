package models.qq.api

/**
 * QQ Authorization  请求体
 *
 * @author 梦境迷离
 * @version 1.0, 2019-06-27
 */

class QQAccessTokenRequest(grantType: String, clientId: String, clientSecret: String, code: String, redirectUri: String) {
  override def toString: String = s"grant_type=$grantType&client_id=$clientId&client_secret=$clientSecret&code=$code&redirect_uri=$redirectUri"
}
