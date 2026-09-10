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

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun phaseOneConfig(name: String): String =
    providers.gradleProperty(name).orNull
        ?: localProperties.getProperty(name)
        ?: System.getenv(name)
        ?: ""

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

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

        // Phase 1 centralizes configuration only; backend features remain for later phases.
        buildConfigField("String", "SUPABASE_URL", phaseOneConfig("WEGLOW_SUPABASE_URL").asBuildConfigString())
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            phaseOneConfig("WEGLOW_SUPABASE_PUBLISHABLE_KEY").asBuildConfigString(),
        )
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
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
    androidResources { noCompress += "onnx" }
    testOptions.unitTests.all {
        it.systemProperty("weglow.model.assets", file("src/main/assets/acne").absolutePath)
    }
}

abstract class ArchitectureCheckTask : DefaultTask() {
    @get:InputDirectory
    abstract val sourceRoot: DirectoryProperty

    @TaskAction
    fun verifyBoundaries() {
        val rootDir = sourceRoot.get().asFile
        val violations = mutableListOf<String>()

        fun scan(relativePath: String, forbidden: List<String>) {
            val root = rootDir.resolve(relativePath)
            if (!root.exists()) return
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { sourceFile ->
                    val text = sourceFile.readText()
                    forbidden.forEach { token ->
                        if (text.contains(token)) {
                            violations += "${sourceFile.relativeTo(rootDir)} imports/uses forbidden dependency: $token"
                        }
                    }
                }
        }

        scan("domain", listOf("import android.", "import androidx.", "io.github.jan.supabase", "com.example.weglow.data", "androidx.compose"))
        scan("feature", listOf("io.github.jan.supabase", "com.example.weglow.data.remote"))
        scan("ui", listOf("io.github.jan.supabase", "com.example.weglow.data.remote"))

        if (violations.isNotEmpty()) {
            throw GradleException("Architecture boundary violations:\n" + violations.joinToString("\n"))
        }
    }
}

val architectureCheck by tasks.registering(ArchitectureCheckTask::class) {
    group = "verification"
    description = "Enforces WeGlow Phase 1 dependency boundaries."
    sourceRoot.set(layout.projectDirectory.dir("src/main/java/com/example/weglow"))
}

tasks.named("preBuild").configure { dependsOn(architectureCheck) }

dependencies {
    implementation(libs.onnxruntime.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    testRuntimeOnly(libs.onnxruntime.desktop)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
