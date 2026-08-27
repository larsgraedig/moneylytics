plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.jib.conventions)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    jacoco
    idea
}

group = "com.moneylytics"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.client)
    implementation(libs.reactor.kotlin.extensions)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.reactor)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.commons.csv)
    implementation(libs.kotlin.logging)
    testRuntimeOnly(libs.h2)
    runtimeOnly(libs.postgresql)
    implementation(libs.spring.session.jdbc)
    implementation(libs.springdoc.openapi.webflux.ui)
    implementation(libs.stripe.java)
    implementation(libs.liquibase.core)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.micrometer.tracing.bridge.otel)
    implementation(libs.opentelemetry.exporter.otlp)
    testImplementation(libs.spring.boot.starter.webflux.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.kotlin)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("${rootProject.projectDir}/detekt.yml")
}

ktlint {
    filter {
        exclude { it.file.path.contains("generated") }
    }
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}
