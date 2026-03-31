plugins {
    java
    id("com.diffplug.spotless")
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    testImplementation("org.springframework:spring-webflux:6.1.10")
    testImplementation("org.springframework:spring-context:6.1.10")
    testImplementation("io.projectreactor.netty:reactor-netty-http:1.1.20")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("started", "passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

spotless {
    java {
        googleJavaFormat("1.25.2")
        targetExclude("build/**")
    }
}
