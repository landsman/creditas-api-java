plugins {
    `java-library`
}

description = "A client for Creditas API, Banka CREDITAS's API opened with a key the account holder issues"

dependencies {
    // Nullability annotations are part of the API, so Kotlin callers see String and String? correctly.
    api("org.jspecify:jspecify:1.0.1")
    // No Jackson type leaves the library, so a caller on Jackson 2 keeps it.
    implementation("tools.jackson.core:jackson-databind:3.3.0")
    // JacksonException, the one type the client catches, lives here.
    implementation("tools.jackson.core:jackson-core:3.3.0")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    // Pinned, so a JPMS caller's `requires` does not change with the jar's file name.
    manifest { attributes("Automatic-Module-Name" to "cz.insuit.creditas") }
}
