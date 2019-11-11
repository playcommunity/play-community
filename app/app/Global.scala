package app

import models.{Link, SiteSetting}

/**
  * 系统全局缓存，通过CDC实时监听数据库变化
  */
object Global {
  //系统版本
  var version = "1.2.0"

  //是否启用搜索
  var esEnabled = false

  //ES是否就绪
  var isElasticReady = false

  //网站首页地址
  var homeUrl = "https://www.playscala.cn"

  var siteSetting = SiteSetting(
    "PlayScala社区",
    "http://www.playscala.cn",
    "PlayScala社区 - Scala & Akka & Play Framework 技术交流平台",
    "/assets/images/logo.png", List.empty[Link], "/assets/favicon.ico",
    "Scala社区,Scala论坛,Scala中国,Scala中文,Scala入门,Scala指南,Scala教程,Scala开发,Play社区,Play论坛,Play中国,Play中文,Play框架社区,Play开发社区,Play Framework社区,Play Framework教程,Play Framework入门,Play Framework指南",
    "PlayScala社区是一个以Scala和Play Framework为中心的开发社区，为Play Framework开发者提供一个学习和交流平台。通过社区的力量，不断地沉淀、整理Play Framework相关知识，让初学者更快地融入Play Framework大家庭。"
  )

  // FIXME: to be removed.
  var appCodes: List[String] = List.empty[String]

}
