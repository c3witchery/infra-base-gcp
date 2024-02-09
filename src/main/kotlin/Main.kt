import com.pulumi.Pulumi
import corda.gkeHelmStack


fun main(args: Array<String>) {

  Pulumi.run {
      //ctx -> infraGkeStack(ctx)
      ctx -> gkeHelmStack(ctx)
    }



}