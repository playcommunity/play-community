import com.google.inject.AbstractModule
import java.time.{Clock, ZoneOffset}

import play.engineio.EngineIOController
import services.InitializeService
import socketio.MySocketIOEngineProvider

class Module extends AbstractModule {

  override def configure() = {
    bind(classOf[InitializeService]).asEagerSingleton
  }

}
