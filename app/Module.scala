import cn.playscala.mongo.Mongo
import com.google.inject.AbstractModule
import security.PasswordEncoder
import security.impl.Argon2PasswordEncoder
import services.InitializeService

class Module extends AbstractModule {
  override def configure() = {

    Mongo.setModelsPackage("models")

    bind(classOf[InitializeService]).asEagerSingleton
    bind(classOf[PasswordEncoder]).to(classOf[Argon2PasswordEncoder]).asEagerSingleton()

  }
}
