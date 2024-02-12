package corda


import kotlinx.serialization.Serializable

@Serializable
data class ClusterConfig(
    val apiVersion: String,
    val clusters: List<Cluster>,
    val contexts: List<Context>,
    val currentContext: String,
    val kind: String,
    val preferences: Map<String, String>,
    val users: List<User>
)

@Serializable
data class Cluster(
    val cluster: ClusterData,
    val name: String
)

@Serializable
data class ClusterData(
    val certificateAuthorityData: String,
    val server: String
)

@Serializable
data class Context(
    val context: ContextData,
    val name: String
)

@Serializable
data class ContextData(
    val cluster: String,
    val user: String
)

@Serializable
data class User(
    val name: String,
    val user: UserConfig
)

@Serializable
data class UserConfig(
    val authProvider: AuthProvider
)

@Serializable
data class AuthProvider(
    val config: AuthConfig,
    val name: String
)

@Serializable
data class AuthConfig(
    val cmdArgs: String,
    val cmdPath: String,
    val expiryKey: String,
    val tokenKey: String
)