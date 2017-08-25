package socketio

import play.api._
import play.api.inject.Module
import play.engineio.EngineIOController

class MyModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration) = Seq(
    bind[EngineIOController].toProvider[MySocketIOEngineProvider]
  )
}
