package models

import cn.playscala.mongo.Mongo
import play.api.inject.Injector

/**
  * 领域服务入口
  */
object DomainRegistry {
  private var _injector: Injector = null

  lazy val mongo = _injector.instanceOf(classOf[Mongo])

  def setInjector(injector: Injector): Unit = {
    this._injector = injector
  }
}
