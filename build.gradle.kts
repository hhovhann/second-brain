plugins {
    java
    application
}

group = "com.hhovhann"
version = "0.0.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(27)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // LangChain4j core only: the model clients and JSON-schema types. No Spring —
    // this is a local CLI and MCP tool; see docs/DECISIONS.md D4.
    implementation(platform("dev.langchain4j:langchain4j-bom:1.20.2"))
    implementation("dev.langchain4j:langchain4j")
    implementation("dev.langchain4j:langchain4j-open-ai")
    implementation("dev.langchain4j:langchain4j-http-client-jdk")

    // Reading documents: PDF and web pages. Word and PowerPoint files are plain zip + XML, no library.
    implementation("org.apache.pdfbox:pdfbox:3.0.4")
    implementation("org.jsoup:jsoup:1.19.1")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "com.hhovhann.brain.Brain"
    // StructuredTaskScope is a preview API in Java 27 (JEP 533). Every use goes
    // through one helper class, so the day it is final this flag goes away.
    applicationDefaultJvmArgs = listOf("--enable-preview")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--enable-preview", "-Xlint:all", "-parameters"))
}

tasks.test {
    jvmArgs("--enable-preview")
    useJUnitPlatform {
        excludeTags("measure")
    }
}

// Measurements against the live models and the benchmark projects, not unit tests:
//   ./gradlew measure
tasks.register<Test>("measure") {
    description = "Runs the measurements tagged 'measure' against live models."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    jvmArgs("--enable-preview")
    useJUnitPlatform {
        includeTags("measure")
    }
    testLogging.showStandardStreams = true
    outputs.upToDateWhen { false }
}
