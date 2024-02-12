package corda
//
//import com.pulumi.Context
//import com.pulumi.Pulumi
//import com.pulumi.core.Output
//import com.pulumi.gcp.Config
//import com.pulumi.gcp.container.*
//import com.pulumi.gcp.container.inputs.NodePoolManagementArgs
//import com.pulumi.gcp.container.inputs.NodePoolNodeConfigArgs
//import com.pulumi.gcp.container.outputs.ClusterMasterAuth
//import com.pulumi.gcp.container.outputs.GetEngineVersionsResult
//import com.pulumi.kubernetes.Provider
//import com.pulumi.kubernetes.ProviderArgs
//import com.pulumi.kubernetes.apps.v1.Deployment
//import com.pulumi.kubernetes.apps.v1.DeploymentArgs
//import com.pulumi.kubernetes.apps.v1.inputs.DeploymentSpecArgs
//import com.pulumi.kubernetes.core.v1.Namespace
//import com.pulumi.kubernetes.core.v1.NamespaceArgs
//import com.pulumi.kubernetes.core.v1.Service
//import com.pulumi.kubernetes.core.v1.ServiceArgs
//import com.pulumi.kubernetes.core.v1.enums.ServiceSpecType
//import com.pulumi.kubernetes.core.v1.inputs.*
//import com.pulumi.kubernetes.core.v1.outputs.LoadBalancerStatus
//import com.pulumi.kubernetes.core.v1.outputs.ServiceStatus
//import com.pulumi.kubernetes.meta.v1.inputs.LabelSelectorArgs
//import com.pulumi.kubernetes.meta.v1.inputs.ObjectMetaArgs
//import com.pulumi.kubernetes.meta.v1.outputs.ObjectMeta
//import com.pulumi.resources.CustomResourceOptions
//import java.text.MessageFormat
//import java.util.*
//import java.util.Map
//import java.util.function.Function
//
//
//
//
//    private fun simpleGkestack(ctx: Context) {
//        val name = "helloworld"
//        val masterVersion = ctx.config()["masterVersion"].map<Output<String>> { value: String? ->
//            Output.of(
//                value
//            )
//        }
//            .orElseGet {
//                ContainerFunctions.getEngineVersions()
//                    .applyValue { versions: GetEngineVersionsResult -> versions.latestMasterVersion() }
//            }
//        ctx.export("masterVersion", masterVersion)
//
//        // Create a GKE cluster
//        // We can't create a cluster with no node pool defined, but we want to only use
//        // separately managed node pools. So we create the smallest possible default
//        // node pool and immediately delete it.
//        val cluster = Cluster(
//            name,
//            ClusterArgs.builder().initialNodeCount(1)
//                .removeDefaultNodePool(true)
//                .minMasterVersion(masterVersion)
//                .build()
//        )
//        val nodePool = NodePool(
//            "primary-node-pool",
//            NodePoolArgs.builder()
//                .cluster(cluster.name())
//                .location(cluster.location())
//                .version(masterVersion)
//                .initialNodeCount(2)
//                .nodeConfig(
//                    NodePoolNodeConfigArgs.builder()
//                        .preemptible(true)
//                        .machineType("n1-standard-1")
//                        .oauthScopes(
//                            "https://www.googleapis.com/auth/compute",
//                            "https://www.googleapis.com/auth/devstorage.read_only",
//                            "https://www.googleapis.com/auth/logging.write",
//                            "https://www.googleapis.com/auth/monitoring"
//                        )
//                        .build()
//                )
//                .management(
//                    NodePoolManagementArgs.builder()
//                        .autoRepair(true)
//                        .build()
//                )
//                .build(),
//            CustomResourceOptions.builder()
//                .dependsOn(cluster)
//                .build()
//        )
//        ctx.export("clusterName", cluster.name())
//
//        // Manufacture a GKE-style kubeconfig. Note that this is slightly "different"
//        // because of the way GKE requires gcloud to be in the picture for cluster
//        // authentication (rather than using the client cert/key directly).
//        val gcpConfig = Config()
//        val clusterName = String.format(
//            "%s_%s_%s",
//            gcpConfig.project().orElseThrow(),
//            gcpConfig.zone().orElseThrow(),
//            name
//        )
//        val masterAuthClusterCaCertificate = cluster.masterAuth()
//            .applyValue { a: ClusterMasterAuth ->
//                a.clusterCaCertificate().orElseThrow()
//            }
//        val kubeconfig = cluster.endpoint()
//            .apply { endpoint: String? ->
//                masterAuthClusterCaCertificate.applyValue { caCert: String? ->
//                    MessageFormat.format(
//                        java.lang.String.join(
//                            "\n",
//                            "apiVersion: v1",
//                            "clusters:",
//                            "- cluster:",
//                            "    certificate-authority-data: {2}",
//                            "    server: https://{1}",
//                            "  name: {0}",
//                            "contexts:",
//                            "- context:",
//                            "    cluster: {0}",
//                            "    user: {0}",
//                            "  name: {0}",
//                            "current-context: {0}",
//                            "kind: Config",
//                            "preferences: '{}'",
//                            "users:",
//                            "- name: {0}",
//                            "  user:",
//                            "    auth-provider:",
//                            "      config:",
//                            "        cmd-args: config config-helper --format=json",
//                            "        cmd-path: gcloud",
//                            "        expiry-key: \"'{.credential.token_expiry}'\"",
//                            "        token-key: \"'{.credential.access_token}'\"",
//                            "      name: gcp"
//                        ), clusterName, endpoint, caCert
//                    )
//                }
//            }
//        ctx.export("kubeconfig", kubeconfig)
//
//        // Create a Kubernetes provider instance that uses our cluster from above.
//        val clusterProvider = Provider(
//            name,
//            ProviderArgs.builder()
//                .kubeconfig(kubeconfig)
//                .build(),
//            CustomResourceOptions.builder()
//                .dependsOn(nodePool, cluster)
//                .build()
//        )
//        val clusterResourceOptions = CustomResourceOptions.builder()
//            .provider(clusterProvider)
//            .build()
//
//        // Create a Kubernetes Namespace
//        val ns = Namespace(
//            name,
//            NamespaceArgs.Empty,
//            clusterResourceOptions
//        )
//
//        // Export the Namespace name
//        val namespaceName = ns.metadata()
//            .applyValue<Any>(Function<ObjectMeta, Any> { m: ObjectMeta ->
//                m.name().orElseThrow()
//            })
//        ctx.export("namespaceName", namespaceName)
//
//        val appLabels = Map.of("appClass", name)
//
//        val metadata: ObjectMetaArgs = ObjectMetaArgs.builder().namespace(namespaceName)
//            .labels(appLabels)
//            .build()
//
//        // Create a NGINX Deployment
//        val deployment = Deployment(
//            name, DeploymentArgs.builder()
//                .metadata(metadata)
//                .spec(
//                    DeploymentSpecArgs.builder()
//                        .replicas(1)
//                        .selector(
//                            LabelSelectorArgs.builder()
//                                .matchLabels(appLabels)
//                                .build()
//                        )
//                        .template(
//                            PodTemplateSpecArgs.builder()
//                                .metadata(metadata)
//                                .spec(
//                                    PodSpecArgs.builder()
//                                        .containers(
//                                            ContainerArgs.builder()
//                                                .name(name)
//                                                .image("nginx:latest")
//                                                .ports(
//                                                    ContainerPortArgs.builder()
//                                                        .name("http")
//                                                        .containerPort(80)
//                                                        .build()
//                                                )
//                                                .build()
//                                        )
//                                        .build()
//                                )
//                                .build()
//                        )
//                        .build()
//                )
//                .build(), clusterResourceOptions
//        )
//
//        // Export the Deployment name
//        ctx.export("deploymentName", deployment.metadata()
//            .applyValue<Any>(Function<ObjectMeta, Any> { m: ObjectMeta ->
//                m.name().orElseThrow()
//            })
//        )
//
//        // Create a LoadBalancer Service for the NGINX Deployment
//        val service = Service(
//            name, ServiceArgs.builder()
//                .metadata(metadata)
//                .spec(
//                    ServiceSpecArgs.builder()
//                        .type(Output.ofRight(ServiceSpecType.LoadBalancer))
//                        .ports(
//                            ServicePortArgs.builder()
//                                .port(80)
//                                .targetPort(Output.ofRight("http"))
//                                .build()
//                        )
//                        .selector(appLabels)
//                        .build()
//                )
//                .build(), clusterResourceOptions
//        )
//
//        // Export the Service name and public LoadBalancer endpoint
//        ctx.export("serviceName", service.metadata()
//            .applyValue<Any>(Function<ObjectMeta, Any> { m: ObjectMeta ->
//                m.name().orElseThrow()
//            })
//        )
//        ctx.export("servicePublicIP", service.status()
//            .applyValue { s: Optional<ServiceStatus> ->
//                s.orElseThrow().loadBalancer().orElseThrow()
//            }
//            .applyValue { status: LoadBalancerStatus ->
//                status.ingress()[0].ip().orElseThrow()
//            })
//    }
//}