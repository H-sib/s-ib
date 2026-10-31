import com.android.build.api.artifact.SingleArtifact
import groovy.json.JsonSlurper
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Signing material lives in release/keystore.properties (git-ignored). Absent file means an unsigned release build.
val signingProps = Properties().apply {
    val f = rootProject.file("release/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// The Firebase connection. The owner downloads google-services.json from the Firebase console into app/ (git-ignored).
// Present: sharing and Google sign in are switched on and the network permissions are kept. Absent: sharing stays off and the
// merged manifest has no network permission at all, which tools/consistency-check.py verifies against the privacy text.
@Suppress("UNCHECKED_CAST")
val firebase: Map<String, String>? = file("google-services.json").takeIf { it.exists() }?.let { f ->
    val root = JsonSlurper().parse(f) as Map<String, Any?>
    val project = root["project_info"] as Map<String, Any?>
    val clients = (root["client"] as? List<Map<String, Any?>>).orEmpty()
    val mine = clients.firstOrNull { c ->
        (((c["client_info"] as? Map<String, Any?>)?.get("android_client_info")) as? Map<String, Any?>)?.get("package_name") == "com.hsib.sib"
    }
    if (mine == null) {
        logger.warn("google-services.json has no Android app for com.hsib.sib: sharing stays off.")
        null
    } else {
        val webId = (mine["oauth_client"] as? List<Map<String, Any?>>).orEmpty()
            .firstOrNull { (it["client_type"] as? Number)?.toInt() == 3 }?.get("client_id") as? String
        val key = ((mine["api_key"] as? List<Map<String, Any?>>)?.firstOrNull()?.get("current_key")) as? String
        if (webId == null || key == null) {
            logger.warn("google-services.json lacks a web client id or API key (turn on the Google sign in provider, then download it again): sharing stays off.")
            null
        } else {
            mapOf(
                "apiKey" to key,
                "appId" to ((mine["client_info"] as Map<String, Any?>)["mobilesdk_app_id"] as String),
                "projectId" to (project["project_id"] as String),
                "webClientId" to webId,
            )
        }
    }
}

/**
 * Without a Firebase connection the finished manifest must contain no network permission, whatever the libraries ask for.
 * This rewrites the merged manifest of a variant; with a connection it is not registered and the permissions stay.
 */
abstract class StripNetworkPermissions : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val merged: RegularFileProperty

    @get:OutputFile
    abstract val stripped: RegularFileProperty

    @TaskAction
    fun run() {
        // A whole line such as:  <uses-permission android:name="android.permission.INTERNET" />  (any attributes or layout)
        val permission = Regex("""[ \t]*<uses-permission(-sdk-23)?\s+android:name="android\.permission\.(INTERNET|ACCESS_NETWORK_STATE)"[^>]*/>[ \t]*\r?\n?""")
        stripped.get().asFile.writeText(merged.get().asFile.readText().replace(permission, ""))
    }
}

androidComponents {
    onVariants { variant ->
        if (firebase == null) {
            val task = tasks.register<StripNetworkPermissions>("${variant.name}StripNetworkPermissions")
            variant.artifacts.use(task).wiredWithFiles(StripNetworkPermissions::merged, StripNetworkPermissions::stripped).toTransform(SingleArtifact.MERGED_MANIFEST)
        }
    }
}

android {
    namespace = "com.hsib.sib"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hsib.sib"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "FIREBASE_READY", (firebase != null).toString())
        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebase?.get("apiKey").orEmpty()}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${firebase?.get("appId").orEmpty()}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebase?.get("projectId").orEmpty()}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${firebase?.get("webClientId").orEmpty()}\"")
    }

    signingConfigs {
        if (!signingProps.isEmpty) {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The exported database schemas, so the migration test can build any shipped version of the database (debug builds only).
    sourceSets {
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            // Screenshot tests only write images when asked: gradlew :app:testDebugUnitTest -Precord=true
            all { test -> test.systemProperty("roborazzi.test.record", providers.gradleProperty("record").getOrElse("false")) }
        }
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/LICENSE*", "META-INF/DEPENDENCIES")
        }
        jniLibs {
            // Native libraries must be 16 KB page aligned for Android 15+ devices on Play.
            useLegacyPackaging = false
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.tink.android)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.document.scanner)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Sharing and chat. Google sign in through Android's Credential Manager, accounts in Firebase Authentication, sealed
    // data in Cloud Firestore, App Check so only this app can use the project. Dormant without google-services.json.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    debugImplementation(libs.firebase.appcheck.debug)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.room.testing)
}
