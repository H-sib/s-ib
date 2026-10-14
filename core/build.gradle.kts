plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Pure JVM: no Android imports are allowed in this module (enforced by checkNoAndroidImports).
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.tink.android)
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
    testLogging { events("failed"); showStandardStreams = false; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

// Architecture rule from the proposal: the core engine must stay framework free.
val checkNoAndroidImports = tasks.register("checkNoAndroidImports") {
    group = "verification"
    description = "Fails if any core source imports android.* or androidx.*"
    val sources = fileTree("src") { include("**/*.kt") }
    inputs.files(sources)
    doLast {
        val offenders = sources.files.filter { f ->
            f.readLines().any { it.trimStart().startsWith("import android.") || it.trimStart().startsWith("import androidx.") }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException("Android imports found in :core -> " + offenders.joinToString { it.name })
        }
    }
}
tasks.named("check") { dependsOn(checkNoAndroidImports) }
