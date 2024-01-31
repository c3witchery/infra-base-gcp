package infra

import com.pulumi.Context
import com.pulumi.core.Output
import com.pulumi.gcp.container.Cluster
import com.pulumi.gcp.container.ClusterArgs

import com.pulumi.gcp.compute.Network
import com.pulumi.gcp.compute.NetworkArgs
import com.pulumi.gcp.compute.Subnetwork
import com.pulumi.gcp.compute.SubnetworkArgs
import com.pulumi.gcp.serviceaccount.Account
import com.pulumi.gcp.serviceaccount.AccountArgs


import com.pulumi.gcp.container.ContainerFunctions
import com.pulumi.gcp.container.NodePool
import com.pulumi.gcp.container.NodePoolArgs
import com.pulumi.gcp.container.inputs.NodePoolManagementArgs
import com.pulumi.gcp.container.inputs.NodePoolNodeConfigArgs
import com.pulumi.gcp.container.outputs.GetEngineVersionsResult
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
import com.pulumi.kubernetes.core.v1.inputs.ContainerArgs
import com.pulumi.kubernetes.core.v1.inputs.ContainerPortArgs
import com.pulumi.kubernetes.core.v1.inputs.PodSpecArgs
import com.pulumi.kubernetes.core.v1.inputs.PodTemplateSpecArgs
import com.pulumi.kubernetes.core.v1.inputs.ServicePortArgs
import com.pulumi.kubernetes.core.v1.inputs.ServiceSpecArgs
import com.pulumi.kubernetes.meta.v1.inputs.LabelSelectorArgs
import com.pulumi.kubernetes.meta.v1.inputs.ObjectMetaArgs
import com.pulumi.resources.CustomResourceOptions
import com.pulumi.gcp.container.inputs.*

fun infraGcpStack(ctx: Context) {
            ctx.export("stackInit", Output.of("GCP Stack has been initialized"))

            // Create a new network
            val network = Network("k8s-corda-network",
                NetworkArgs.builder().build()
            )

            // Create a subnet in the new network
            val subnet = Subnetwork(
                "k8s-subnet",
                SubnetworkArgs.
                            builder().
                            ipCidrRange("10.2.0.0/16").
                            network(network.selfLink()).
                            region("us-central1").build()
                )

            // Create a cluster in the new network and subnet
            val cluster = Cluster(
                "k8s-cluster",
                ClusterArgs.builder().
                                initialNodeCount(1).
                                nodeConfig(
                                        ClusterNodeConfigArgs.builder()
                                            .machineType("n1-standard-1")
                                            .build()
                                ).
                                network(network.selfLink()).
                                subnetwork(subnet.selfLink()).
                build()
            )
    
            //https://github.com/pulumi/examples/blob/master/gcp-java-gke-hello-world/src/main/java/gcpgke/App.java





}