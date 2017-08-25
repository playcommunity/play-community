package socketio

import javax.inject.{Inject, Provider, Singleton}
import play.engineio.EngineIOController

@Singleton
class MySocketIOEngineProvider @Inject() (chatEngine: ChatEngine)
  extends Provider[EngineIOController] {

  override lazy val get = chatEngine.controller
}
