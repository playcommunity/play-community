import com.google.inject.AbstractModule
import services.InitializeService

class Module extends AbstractModule {

  override def configure() = {
    bind(classOf[InitializeService]).asEagerSingleton
  }

}
