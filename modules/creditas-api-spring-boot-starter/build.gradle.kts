plugins {
    `java-library`
}

description = "Spring Boot auto-configuration for the Creditas API client"

// Compiled against the oldest Spring Boot it supports; the application's own Spring Boot is what runs it.
// Dependabot leaves this BOM alone (.github/dependabot.yml), so the floor does not move by itself.
val springBoot = dependencies.platform("org.springframework.boot:spring-boot-dependencies:3.5.16")

// The tests run on 3.5.16 too, unless -PtestSpringBoot names another: CI runs them again on Spring Boot 4, the version
// an application is likelier to bring, so a call that moved there fails here rather than in a caller's app.
val testSpringBoot =
    dependencies.platform(
        "org.springframework.boot:spring-boot-dependencies:" +
            providers.gradleProperty("testSpringBoot").getOrElse("3.5.16"),
    )

dependencies {
    api(project(":modules:creditas-api"))
    // CreditasProperties carries its nullability too; declared, not borrowed from the client.
    api("org.jspecify:jspecify:1.0.1")
    compileOnly(springBoot)
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework.boot:spring-boot")
    compileOnly("org.springframework:spring-context")
    // Writes the metadata IntelliJ reads to complete creditas.* in application.yml.
    annotationProcessor(springBoot)
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(testSpringBoot)
    testImplementation("org.springframework.boot:spring-boot")
    testImplementation("org.springframework.boot:spring-boot-autoconfigure")
    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.springframework:spring-context")
    // ApplicationContextRunner hands the test an AssertJ type, so it has to be on the classpath.
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyAnalysis {
    issues {
        // Never imported, but AssertableApplicationContext extends its AssertProvider, so javac needs it.
        onUnusedDependencies { exclude("org.assertj:assertj-core") }
        // Asked for because a test declares a @Configuration; the tests have no properties to describe.
        onUsedTransitiveDependencies { exclude("org.springframework.boot:spring-boot-configuration-processor") }
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.jar {
    manifest { attributes("Automatic-Module-Name" to "cz.insuit.creditas.spring") }
}
