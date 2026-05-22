plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.google.cloud.tools.jib:com.google.cloud.tools.jib.gradle.plugin:3.5.2")
}
