import cn.playscala.mongo.Mongo
import com.google.inject.AbstractModule
import org.bson.codecs.configuration.CodecRegistries.fromProviders
import services.InitializeService
import cn.playscala.mongo.codecs.Macros._
import org.bson.codecs.configuration.CodecRegistries.{fromProviders}

class Module extends AbstractModule {

  override def configure() = {

    Mongo.setModelsPackage("models")

    bind(classOf[InitializeService]).asEagerSingleton

  }

}
