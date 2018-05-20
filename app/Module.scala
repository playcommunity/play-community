import cn.playscala.mongo.Mongo
import com.google.inject.AbstractModule
import services.InitializeService

class Module extends AbstractModule {

  override def configure() = {

    Mongo.setModelsPackage("models")

    bind(classOf[InitializeService]).asEagerSingleton
  }

}
