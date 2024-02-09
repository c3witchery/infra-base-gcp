package corda


import com.pulumi.Context
import com.pulumi.core.Output
import com.pulumi.gcp.Config
import com.pulumi.gcp.compute.Network
import com.pulumi.gcp.compute.NetworkArgs
import com.pulumi.gcp.compute.Subnetwork
import com.pulumi.gcp.compute.SubnetworkArgs
import com.pulumi.gcp.container.*
import com.pulumi.gcp.container.inputs.*
import com.pulumi.kubernetes.Provider
import com.pulumi.kubernetes.ProviderArgs
import com.pulumi.kubernetes.apps.v1.Deployment
import com.pulumi.kubernetes.apps.v1.DeploymentArgs
import com.pulumi.kubernetes.apps.v1.inputs.DeploymentSpecArgs
import com.pulumi.kubernetes.core.v1.Namespace
import com.pulumi.kubernetes.core.v1.NamespaceArgs
import com.pulumi.kubernetes.core.v1.Service
import com.pulumi.kubernetes.core.v1.ServiceArgs
import com.pulumi.kubernetes.core.v1.enums.ServiceSpecType
import com.pulumi.kubernetes.core.v1.inputs.*
import com.pulumi.kubernetes.meta.v1.inputs.LabelSelectorArgs
import com.pulumi.kubernetes.meta.v1.inputs.ObjectMetaArgs
import com.pulumi.resources.CustomResourceOptions
import java.text.MessageFormat
import com.pulumi.kubernetes.helm.v3.Release
import com.pulumi.kubernetes.helm.v3.ReleaseArgs
import java.io.File


//TODO Variable to be externalised perhaps via JSON file
private const val PROJECT ="r3-ps-test-01"
private const val NAME = "corda-v51"
private const val REGION = "us-central1"
private const val CIDR_RANGE = "10.2.0.0/16"
private const val MACHINE_TYPE = "n1-standard-1"
private const val CORDA = "corda"

fun gkeHelmStack(ctx: Context) {

    // Create a new network
    val network = Network(
        NAME.plus("-network"),
        NetworkArgs.builder()
            .autoCreateSubnetworks(false)
            .build()
    )

    // Create a subnet in the new network
    val subnet = Subnetwork(
        NAME.plus("-subnet"),
        SubnetworkArgs.builder()
            .ipCidrRange(CIDR_RANGE)
            .network(network.selfLink())
            .region(REGION)
            .build(),
        CustomResourceOptions.builder()
            .dependsOn(network)
            .build()
    )

    // Create a cluster in the new network and subnet
    val cluster = Cluster(
        NAME.plus("-cluster"),
        ClusterArgs.builder()
            .initialNodeCount(1)
            .location(REGION)
            .removeDefaultNodePool(true)
            .network(network.selfLink())
            .subnetwork(subnet.selfLink())
            .build(),
        CustomResourceOptions.builder()
            .dependsOn(subnet)
            .build()
    )

    // Manufacture a GKE-style kubeconfig for authentication
    val clusterName = String.format(
        "%s_%s_%s",
        PROJECT,
        REGION,
        NAME
    )



    val nodePool = NodePool(
        "primary-node-pool",
        NodePoolArgs.builder()
            .cluster(cluster.name())
            .location(cluster.location())
            .initialNodeCount(2)
            .nodeConfig(
                NodePoolNodeConfigArgs.builder()
                    .preemptible(true)
                    .machineType(MACHINE_TYPE)
                    .oauthScopes(
                        "https://www.googleapis.com/auth/compute",
                        "https://www.googleapis.com/auth/devstorage.read_only",
                        "https://www.googleapis.com/auth/logging.write",
                        "https://www.googleapis.com/auth/monitoring"
                    )
                    .build()
            )
            .management(
                NodePoolManagementArgs.builder()
                    .autoRepair(true)
                    .build()
            )
            .build(),
        CustomResourceOptions.builder()
            .dependsOn(cluster)
            .build()
    )

    var kubeconfig = cluster.name().apply{
            name -> cluster.endpoint().applyValue{
            endpoint ->
            "apiVersion: v1\n" +
                    "clusters:\n" +
                    "- cluster:\n" +
                    "    certificate-authority-data: " + cluster.masterAuth().applyValue { auth -> auth.clusterCaCertificate() } + "\n" +
                    "    server: https://" + endpoint + "\n" +
                    "  name: " + name + "\n" +
                    "contexts:\n" +
                    "- context:\n" +
                    "    cluster: " + name + "\n" +
                    "    user: " + name + "\n" +
                    "  name: " + name + "\n" +
                    "current-context: " + name + "\n" +
                    "kind: Config\n" +
                    "preferences: {}\n" +
                    "users:\n" +
                    "- name: " + name + "\n" +
                    "  user:\n" +
                    "    auth-provider:\n" +
                    "      config:\n" +
                    "        cmd-args: config config-helper --format=json\n" +
                    "        cmd-path: gcloud\n" +
                    "        expiry-key: '{.credential.token_expiry}'\n" +
                    "        token-key: '{.credential.access_token}'\n" +
                    "      name: gcp\n"
        }
    }

    // Create a Kubernetes provider instance that uses our cluster from above.
    val clusterProvider = Provider(
        NAME,
        ProviderArgs.builder()
            .kubeconfig(kubeconfig)
            .build(),
        CustomResourceOptions.builder()
            .deleteBeforeReplace(true)
            .dependsOn(nodePool)
            .build()
    )

    var clusterResourceOptions = CustomResourceOptions.builder().deleteBeforeReplace(true).provider(clusterProvider).build()

    // Create a Kubernetes Namespace
    var ns = Namespace(
        CORDA,
        NamespaceArgs.Empty,
        clusterResourceOptions
    )

    // Export the Namespace name
    var namespaceName = ns.metadata().applyValue { m -> m.name().orElseThrow() }
    ctx.export("namespaceName", namespaceName)

    var appLabels = mapOf("appClass" to CORDA)

    var metadata = ObjectMetaArgs.builder()
        .namespace(namespaceName)
        .labels(appLabels)
        .build()

    // Create a NGINX Deployment
    var deployment = Deployment(
        NAME,
        DeploymentArgs.builder().metadata(metadata).spec(
            DeploymentSpecArgs.builder().replicas(1).selector(
                LabelSelectorArgs.builder()
                    .matchLabels(appLabels)
                    .build()
            ).template(
                PodTemplateSpecArgs.builder()
                    .metadata(metadata)
                    .spec(
                        PodSpecArgs.builder()
                            .containers(
                                ContainerArgs.builder()
                                    .name(NAME)
                                    .image("nginx:latest")
                                    .ports(
                                        ContainerPortArgs.builder()
                                            .name("http")
                                            .containerPort(80)
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
                .build()
        )
            .build(),
        clusterResourceOptions
    )
    ctx.export("deploymentName", deployment.metadata()
        .applyValue { m -> m.name().orElseThrow() }
    )

    // Create a LoadBalancer Service for the NGINX Deployment
    var service = Service(
        NAME,
        ServiceArgs.builder().metadata(metadata).spec(
            ServiceSpecArgs.builder().type(Output.ofRight(ServiceSpecType.LoadBalancer)).ports(
                ServicePortArgs.builder()
                    .port(80)
                    .targetPort(Output.ofRight("http"))
                    .build()
            ).selector(appLabels).build()
        )
            .build(),
        clusterResourceOptions
    )

    // Export the Service name and public LoadBalancer endpoint
    ctx.export("serviceName", service.metadata()
        .applyValue { m -> m.name().orElseThrow() }
    )

    ctx.export(
        "servicePublicIP",
        service.status()
            .applyValue { s -> s.orElseThrow().loadBalancer().orElseThrow() }
            .applyValue { status -> status.ingress()[0].ip().orElseThrow() })

    val prereqsHelmRelease = Release(
        "corda-dev-prereqs",
        ReleaseArgs.builder()
            .chart("oci://registry-1.docker.io/corda/corda-dev-prereqs")
            .namespace(CORDA).timeout(6000)
            .build(),
        clusterResourceOptions
    )
    ctx.export("corda-dev-prereqs", prereqsHelmRelease.name())

    val cordaHelmRelease = Release(
        CORDA,
        ReleaseArgs.builder()
            .chart("oci://registry-1.docker.io/corda/corda")
            .namespace(CORDA).version("5.1.0")
            .build(),
        CustomResourceOptions.builder()
            .dependsOn(prereqsHelmRelease)
            .build()
    )
    ctx.export("cordaHelmRelease", cordaHelmRelease.name())


}