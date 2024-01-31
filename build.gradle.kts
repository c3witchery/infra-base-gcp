plugins {
    application
    kotlin("jvm") version "1.9.0"
}

repositories {
    maven {
        // The google mirror is less flaky than mavenCentral()
        url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
    }
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("com.pulumi:pulumi:(,1.0]")
    implementation("org.virtuslab:pulumi-kotlin:0.9.4.0")

    implementation("org.virtuslab:pulumi-kubernetes-kotlin:4.5.4.0")
    implementation("org.virtuslab:pulumi-gcp-kotlin:7.7.0.0")
    implementation("org.virtuslab:pulumi-google-native-kotlin:0.31.1.1")
    implementation("org.virtuslab:pulumi-docker-kotlin:3.6.1.2")
    implementation("com.pulumi:pulumi:0.9.4")
    implementation("org.virtuslab:pulumi-random-kotlin:4.14.0.0")
    implementation("com.pulumi:pulumi:0.9.5")
    implementation("com.pulumi:command:4.5.0")
}

application {
    mainClass.set(
        project.findProperty("mainClass") as? String ?: "MainKt"
    )


}