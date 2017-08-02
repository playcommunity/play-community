import com.google.inject.AbstractModule
import java.time.{Clock, ZoneOffset}

import services.InitializeService

class Module extends AbstractModule {

  override def configure() = {
    //bind(classOf[InitializeService]).asEagerSingleton
  }

}
