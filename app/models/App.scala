package models

/**
  * Created by xiaomi on 2017/7/30.
  */
object App {
  val version = "1.0.0"
  var siteSetting = SiteSetting(
    "PlayScala社区", "http://www.playscala.cn", "/assets/images/logo.png", List.empty[Link], "/assets/favicon.ico",
    "Play社区,Play框架社区,Play开发社区,Play Framework社区,Play Framework教程,Play Framework入门,Play Framework指南,Scala社区,Scala入门,Scala指南,Scala教程,Scala开发",
    "PlayScala社区是一个以Scala和Play Framework为中心的开发社区，为Play Framework开发者提供一个学习和交流平台。通过社区的力量，不断地沉淀、整理Play Framework相关知识，让初学者更快地融入Play Framework大家庭。"
  )
}

object Role {
  val USER = "user"
  val ADMIN = "admin"
}
