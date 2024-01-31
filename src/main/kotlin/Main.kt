import com.pulumi.Context
import infra.infraGcpStack


fun main(args: Array<String>) {

    com.pulumi.Pulumi.run { ctx ->

        infraGcpStack(ctx)

    }



}