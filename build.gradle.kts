import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("com.diffplug.spotless") version "8.11.0"
    id("com.autonomousapps.dependency-analysis") version "3.20.4"
    id("net.ltgt.errorprone") version "5.2.0" apply false
}

// Compiled once, for release 21, on whatever JDK runs Gradle; the tests run on the JDK -PtestsJdk names, so CI's
// matrix checks the jar on each JDK it supports.
val testsJdk = providers.gradleProperty("testsJdk")

allprojects {
    group = "cz.insuit"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release = 21
    }
    plugins.withType<JavaPlugin> {
        val toolchains = extensions.getByType<JavaToolchainService>()
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            if (testsJdk.isPresent) {
                javaLauncher = toolchains.launcherFor { languageVersion = JavaLanguageVersion.of(testsJdk.get()) }
            }
        }
    }
}

// What ships is held to the strictest checks: every warning fails, and the JSpecify annotations are enforced by
// NullAway rather than left as documentation.
subprojects {
    if (path.startsWith(":modules:")) {
        apply(plugin = "net.ltgt.errorprone")
        apply(plugin = "com.autonomousapps.dependency-analysis")
        dependencies {
            "errorprone"("com.google.errorprone:error_prone_core:2.52.0")
            "errorprone"("com.uber.nullaway:nullaway:0.14.4")
        }
        tasks.withType<JavaCompile>().configureEach {
            options.compilerArgs.addAll(listOf("-Xlint:all,-serial", "-Werror"))
            options.errorprone {
                check("NullAway", CheckSeverity.ERROR)
                option("NullAway:JSpecifyMode", "true")
                option("NullAway:OnlyNullMarked", "true")
            }
        }
        tasks.withType<Javadoc>().configureEach {
            // An @param per record component would only repeat its name; broken links still fail.
            (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
        }
    }
}

// make build fails on a dependency a module declares and does not use, or uses without declaring.
dependencyAnalysis {
    structure {
        // junit-jupiter is the aggregate that brings the engine; declaring -api instead would run no tests.
        bundle("junit-jupiter") { includeGroup("org.junit.jupiter") }
    }
    issues { all { onAny { severity("fail") } } }
}

spotless {
    java {
        target("modules/*/src/**/*.java")
        palantirJavaFormat("2.103.0")
    }
    kotlinGradle {
        target("*.gradle.kts", "modules/*/*.gradle.kts")
        ktlint()
    }
}
