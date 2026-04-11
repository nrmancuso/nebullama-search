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
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("io.github.openfeign:feign-core:13.11")
    testImplementation("io.github.openfeign:feign-jackson:13.11")
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
