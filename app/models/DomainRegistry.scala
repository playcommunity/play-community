package models

import cn.playscala.mongo.Mongo
import domain.infrastructure.repository.mongo.{MongoBoardRepository, MongoResourceRepository, MongoUserRepository}
import play.api.inject.Injector

/**
  * 领域服务入口
  */
object DomainRegistry {
  private var _injector: Injector = null

  lazy val mongo = _injector.instanceOf(classOf[Mongo])

  lazy val userRepo = _injector.instanceOf(classOf[MongoUserRepository])

  lazy val resourceRepo = _injector.instanceOf(classOf[MongoResourceRepository])

  lazy val boardRepo = _injector.instanceOf(classOf[MongoBoardRepository])

  def setInjector(injector: Injector): Unit = {
    this._injector = injector
  }
}
