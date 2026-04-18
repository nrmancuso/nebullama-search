import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless")
    id("checkstyle")
    id("com.github.spotbugs") version "6.0.14"
}

group = "com.example"
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
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.opensearch.client:opensearch-java:2.10.3")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.3.1")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.graphql:spring-graphql-test")
    testImplementation("org.springframework:spring-webflux")
    testImplementation("org.testcontainers:testcontainers:1.20.6")
    testImplementation("org.testcontainers:junit-jupiter:1.20.6")
    testImplementation("org.wiremock:wiremock-jetty12:3.5.4")
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("api.version", "1.41")
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("started", "passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

spotless {
    java {
        googleJavaFormat("1.25.2")
        targetExclude("build/**")
    }
}

checkstyle {
    toolVersion = "13.3.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    configDirectory = file("config/checkstyle")
    isIgnoreFailures = false
}

spotbugs {
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.LOW)
    ignoreFailures.set(true)
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask> {
    reports.register("html") {
        required.set(true)
    }
}
