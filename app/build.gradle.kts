import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")

if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use {
        localProperties.load(it)
    }
}

android {
    namespace = "com.example.weglow"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.weglow"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${localProperties.getProperty("WEGLOW_SUPABASE_URL", "")}\"",
        )

        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            "\"${localProperties.getProperty("WEGLOW_SUPABASE_PUBLISHABLE_KEY", "")}\"",
        )

        buildConfigField(
            "String",
            "WEATHER_API_KEY",
            "\"${localProperties.getProperty("WEATHER_API_KEY", "")}\"",
        )

        buildConfigField(
            "String",
            "CHAT_URL",
            "\"${localProperties.getProperty("WEGLOW_CHAT_URL", "")}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions.unitTests.all {
        it.jvmArgs("--enable-native-access=ALL-UNNAMED")

        it.systemProperty(
            "weglow.model.assets",
            file("src/main/assets/acne").absolutePath,
        )

        it.systemProperty(
            "weglow.hairstyle.assets",
            file("src/main/assets/hairstyle").absolutePath,
        )
    }

    androidResources {
        noCompress += listOf(
            "onnx",
            "ort",
            "tflite",
            "lite",
        )
    }
}

dependencies {

    // Android
    implementation(libs.androidx.core.ktx)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Activity / Compose
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation(libs.kotlinx.coroutines.play.services)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Supabase
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)

    // Ktor
    implementation(libs.ktor.client.android)

    // Retrofit / Gson
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    // OkHttp
    implementation(libs.okhttp.logging.interceptor)

    // Images
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Camera / scanning
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // AI / ML
    implementation(libs.onnxruntime.android)
    implementation(libs.litert)
    implementation(libs.mlkit.face.detection)

    // Location / environment / UV
    implementation(libs.play.services.location)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Android tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Debug
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // ONNX Runtime for JVM/unit tests
    testRuntimeOnly(libs.onnxruntime.desktop)

    // Java 8+ APIs on older Android versions
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

/**
 * Verifies that the main source set keeps the expected architecture boundaries.
 */
abstract class CheckArchitectureTask : DefaultTask() {

    @get:InputDirectory
    abstract val sourceDirectory: DirectoryProperty

    @TaskAction
    fun check() {
        val root = sourceDirectory.get().asFile

        if (!root.exists()) {
            throw GradleException(
                "Source directory does not exist: $root",
            )
        }

        val forbiddenImports = listOf(
            "android.database.",
            "android.content.ContentResolver",
        )

        val violations = mutableListOf<String>()

        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()

                forbiddenImports.forEach { forbidden ->
                    if (text.contains(forbidden)) {
                        violations += "${file.relativeTo(root)} -> $forbidden"
                    }
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Architecture violations found:\n${violations.joinToString("\n")}",
            )
        }
    }
}

tasks.register<CheckArchitectureTask>("checkArchitecture") {
    sourceDirectory.set(
        layout.projectDirectory.dir("src/main/java"),
    )
}
