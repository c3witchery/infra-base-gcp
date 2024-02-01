package infra


import com.pulumi.Context
import com.pulumi.core.Output
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


//TODO Variable to be externalised perhaps via JSON file
private const val name = "k8s-corda-v51-network"

fun infraGcpStack(ctx: Context) {

    // Create a new network
    val network = Network(
        "k8s-corda-network"
        ,
        NetworkArgs.builder().build()
    )

    // Create a subnet in the new network
    val subnet = Subnetwork(
        "k8s-subnet",
        SubnetworkArgs.builder().
                            ipCidrRange("10.2.0.0/16").
                            network(network.selfLink()).
                            region("us-central1").build()
        ,
        CustomResourceOptions.builder()
                    .dependsOn(network)
                    .build()
    )

    // Create a cluster in the new network and subnet
    val cluster = Cluster(
        "corda-k8s-cluster"
        ,
        ClusterArgs.builder().
                            initialNodeCount(1).
                            nodeConfig(
                                        ClusterNodeConfigArgs.builder().
                                            machineType("n1-standard-1").
                                            oauthScopes(
                                            "https://www.googleapis.com/auth/compute",
                                            "https://www.googleapis.com/auth/devstorage.read_only",
                                            "https://www.googleapis.com/auth/logging.write",
                                            "https://www.googleapis.com/auth/monitoring"
                                        ).build()
                            ).
                            network(network.selfLink()).
                            subnetwork(subnet.selfLink()).
                            build(),
                CustomResourceOptions.builder()
                    .dependsOn(network, subnet)
                    .build()
    )

    // Manufacture a GKE-style kubeconfig for authentication
    val gcpConfig = com.pulumi.gcp.Config()
    val clusterName = String.format("%s_%s_%s",
        gcpConfig.project().orElseThrow(),
        gcpConfig.zone().orElseThrow(),
        name
    )

    var masterAuthClusterCaCertificate = cluster.masterAuth().applyValue{
            a -> a.clusterCaCertificate().orElseThrow()
        }

    var yamlTemplate = """
apiVersion: v1,
clusters:,
- cluster:,
    certificate-authority-data: {2},
    server: https://{1},
  name: {0},
contexts:,
- context:,
    cluster: {0},
    user: {0},
  name: {0},
current-context: {0},
kind: Config,
preferences: '{}',
users:,
- name: {0},
  user:
    exec:
      apiVersion: client.authentication.k8s.io/v1beta1
      command: gke-gcloud-auth-plugin
      installHint: Install gke-gcloud-auth-plugin for use with kubectl by following
        https://cloud.google.com/blog/products/containers-kubernetes/kubectl-auth-changes-in-gke
      provideClusterInfo: true
            """

    val kubeconfig: Output<String> = cluster.endpoint().applyValue{endpoint ->
        masterAuthClusterCaCertificate.applyValue{ caCert -> MessageFormat.format(yamlTemplate, clusterName, endpoint, caCert)
        }.toString()
    }
    ctx.export("kubeconfig", kubeconfig);

    val nodePool = NodePool(
        "primary-node-pool",
        NodePoolArgs.builder()
            .cluster(cluster.name())
            .location(cluster.location())
            .initialNodeCount(2)
            .nodeConfig(
                NodePoolNodeConfigArgs.builder()
                    .preemptible(true)
                    .machineType("n1-standard-1")
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

    // Create a Kubernetes provider instance that uses our cluster from above.
    val clusterProvider = Provider(
        name,
        ProviderArgs.builder()
            .kubeconfig(kubeconfig)
            .build(),
        CustomResourceOptions.builder()
            .dependsOn(nodePool, cluster)
            .build()
    )

    var clusterResourceOptions = CustomResourceOptions.builder().provider(clusterProvider).build()

    // Create a Kubernetes Namespace
    var ns =  Namespace(name,
    NamespaceArgs.Empty,
    clusterResourceOptions
    )

    // Export the Namespace name
    var namespaceName = ns.metadata().applyValue{m -> m.name().orElseThrow()}
    ctx.export("namespaceName", namespaceName)

     var appLabels = mapOf("appClass" to name)

     var metadata = ObjectMetaArgs.builder()
        .namespace(namespaceName)
        .labels(appLabels)
        .build()

    // Create a NGINX Deployment
     var deployment =  Deployment(
         name,
         DeploymentArgs.builder().
         metadata(metadata).
         spec(
            DeploymentSpecArgs.builder().
            replicas(1).
            selector(
                LabelSelectorArgs.builder()
                .matchLabels(appLabels)
                .build()).
            template(
                PodTemplateSpecArgs.builder()
                .metadata(metadata)
                .spec(
                    PodSpecArgs.builder()
                    .containers(
                        ContainerArgs.builder()
                        .name(name)
                        .image("nginx:latest")
                        .ports(
                            ContainerPortArgs.builder()
                            .name("http")
                            .containerPort(80)
                            .build())
                        .build())
                    .build())
                .build())
            .build())
        .build()
         ,
         clusterResourceOptions
     )
    ctx.export("deploymentName", deployment.metadata()
        .applyValue{m -> m.name().orElseThrow()}
    )

    // Create a LoadBalancer Service for the NGINX Deployment
     var service =  Service(
         name,
         ServiceArgs.builder().
         metadata(metadata).
         spec(
             ServiceSpecArgs.builder().
             type(Output.ofRight(ServiceSpecType.LoadBalancer)).
             ports(
                 ServicePortArgs.builder()
                .port(80)
                .targetPort(Output.ofRight("http"))
                .build()
             ).
             selector(appLabels).
             build()
            )
        .build()
         ,
         clusterResourceOptions
     )

    // Export the Service name and public LoadBalancer endpoint
    ctx.export("serviceName", service.metadata()
        .applyValue{ m -> m.name().orElseThrow()}
    )

    ctx.export(
        "servicePublicIP"
        ,
        service.status()
            .applyValue{s -> s.orElseThrow().loadBalancer().orElseThrow()}
            .applyValue{status -> status.ingress()[0].ip().orElseThrow()})

}