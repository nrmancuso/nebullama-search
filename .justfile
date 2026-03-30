# Default: show available commands
default:
    @just --list

# Compile
build:
    [working-directory: "service"]
    ./gradlew compileJava

# Run unit tests (excludes integration tests)
[working-directory: "service"]
test:
    ./gradlew test

# Clean all build artifacts
[working-directory: "service"]
clean:
    ./gradlew test

# Run Checkstyle on main sources
[working-directory: "service"]
checkstyle:
    ./gradlew checkstyleMain checkstyleTest

# Build executable fat JAR
[working-directory: "service"]
jar:
    ./gradlew bootJar

# Run Spotless
[working-directory: "service"]
format:
    ./gradlew spotlessApply