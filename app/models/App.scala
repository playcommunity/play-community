package models

/**
  * Created by xiaomi on 2017/7/30.
  */
object App {
  val version = "1.0.0"
  var siteSetting = SiteSetting("PlayScala社区", "http://www.playscala.cn", "/assets/images/logo.png", List.empty[Link], "/assets/favicon.ico", "", "")
}

object Role {
  val COMMON_USER = "user"
  val ADMIN_USER = "admin"
}
