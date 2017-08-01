package models

/**
  * Created by xiaomi on 2017/7/30.
  */
object App {
  var name = "PlayScala社区"
  var logo = "/assets/images/logo.png"
  var url = "http://www.playscala.cn"
  var links = List.empty[Link]
  var favicon = "/assets/favicon.ico"
}

object Role {
  val COMMON_USER = "user"
  val ADMIN_USER = "admin"
}
