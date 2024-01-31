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
    implementation("com.pulumi:pulumi:0.9.5")
    implementation("com.pulumi:kubernetes:(3.0,4.0]")
    implementation("com.pulumi:gcp:(7.0,8.0]")



}

application {
    mainClass.set(
        project.findProperty("mainClass") as? String ?: "MainKt"
    )


}