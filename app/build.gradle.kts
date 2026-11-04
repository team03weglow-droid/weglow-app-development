import java.util.Properties
import java.net.URI
import java.util.Base64
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

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

fun configValue(name: String, fallback: String = ""): String = providers.gradleProperty(name)
    .orElse(providers.environmentVariable(name))
    .orElse(localProperties.getProperty(name, fallback)).get().trim()

fun buildString(value: String): String = "\"" + value.replace("\\", "\\\\")
    .replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n") + "\""

val approvedProductionUrl = "https://nkrxygqtptvarntzxawk.supabase.co"
val developmentUrl = configValue("WEGLOW_DEV_SUPABASE_URL", configValue("WEGLOW_SUPABASE_URL"))
val developmentKey = configValue("WEGLOW_DEV_SUPABASE_PUBLISHABLE_KEY", configValue("WEGLOW_SUPABASE_PUBLISHABLE_KEY"))
val stagingUrl = configValue("WEGLOW_STAGING_SUPABASE_URL")
val stagingKey = configValue("WEGLOW_STAGING_SUPABASE_PUBLISHABLE_KEY")
val releaseUrl = configValue("WEGLOW_PROD_SUPABASE_URL", approvedProductionUrl)
val releaseKey = configValue("WEGLOW_PROD_SUPABASE_PUBLISHABLE_KEY")
val releaseVersionCode = configValue("WEGLOW_VERSION_CODE", "1").toIntOrNull()
    ?.takeIf { it > 0 } ?: error("WEGLOW_VERSION_CODE must be a positive integer.")
val releaseVersionName = configValue("WEGLOW_VERSION_NAME", "1.0").also {
    check(it.isNotBlank()) { "WEGLOW_VERSION_NAME must not be blank." }
}

// Local upload-key credentials are never committed. Android Studio's signing
// wizard can also supply credentials for an individual build.
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}
mapOf("storeFile" to "WEGLOW_UPLOAD_STORE_FILE", "storePassword" to "WEGLOW_UPLOAD_STORE_PASSWORD",
    "keyAlias" to "WEGLOW_UPLOAD_KEY_ALIAS", "keyPassword" to "WEGLOW_UPLOAD_KEY_PASSWORD").forEach { (field, variable) ->
    providers.environmentVariable(variable).orNull?.takeIf { it.isNotBlank() }?.let { keystoreProperties.setProperty(field, it) }
}
val uploadSigningConfigured = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !keystoreProperties.getProperty(it).isNullOrBlank() }
val studioSigningConfigured = providers.gradleProperty("android.injected.signing.store.file").isPresent

abstract class ValidateBackendEnvironmentTask : DefaultTask() {
    @get:Input abstract val environmentName: Property<String>
    @get:Input abstract val backendUrl: Property<String>
    @get:Input abstract val publishableKey: Property<String>
    @get:Input abstract val productionUrl: Property<String>

    @TaskAction fun validate() {
        val label = environmentName.get()
        val url = backendUrl.get()
        val uri = runCatching { URI(url) }.getOrNull()
        check(uri != null && uri.scheme == "https" && uri.host?.endsWith(".supabase.co") == true
            && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.port == -1
            && uri.path.orEmpty() in listOf("", "/")) { "Configure the $label Supabase HTTPS project URL. See docs/ENVIRONMENTS.md." }
        if (label == "production") check(url.trimEnd('/') == productionUrl.get()) { "Release must use the approved new production project." }
        if (label == "staging") check(url.trimEnd('/') != productionUrl.get()) { "Staging must use a separate Supabase project." }
        val key = publishableKey.get()
        val legacyClaims = if (key.startsWith("eyJ")) runCatching {
            val payload = String(Base64.getUrlDecoder().decode(key.split('.')[1]), Charsets.UTF_8)
            groovy.json.JsonSlurper().parseText(payload) as Map<*, *>
        }.getOrNull() else null
        check(key.startsWith("sb_publishable_") && key.length > 20 || legacyClaims?.get("role") == "anon") {
            "Configure the $label publishable/anon key. Secret and service-role keys are forbidden in Android."
        }
        legacyClaims?.get("ref")?.let { reference ->
            check(uri!!.host == "$reference.supabase.co") { "The $label legacy anon key belongs to another project." }
        }
        logger.lifecycle("Validated $label backend configuration (key omitted).")
    }
}

abstract class ValidateUploadSigningTask : DefaultTask() {
    @get:Input abstract val configured: Property<Boolean>
    @TaskAction fun validate() {
        check(configured.get()) { "Release upload signing is missing. Use Android Studio's signed bundle wizard, keystore.properties, or documented CI signing variables. See docs/RELEASE_SIGNING.md." }
    }
}

val validateReleaseEnvironment = tasks.register<ValidateBackendEnvironmentTask>("validateReleaseEnvironment") {
    environmentName.set("production"); backendUrl.set(releaseUrl); publishableKey.set(releaseKey); productionUrl.set(approvedProductionUrl)
}
val validateStagingEnvironment = tasks.register<ValidateBackendEnvironmentTask>("validateStagingEnvironment") {
    environmentName.set("staging"); backendUrl.set(stagingUrl); publishableKey.set(stagingKey); productionUrl.set(approvedProductionUrl)
}
val validateReleaseSigning = tasks.register<ValidateUploadSigningTask>("validateReleaseSigning") {
    configured.set(uploadSigningConfigured || studioSigningConfigured)
}

android {
    namespace = "com.example.weglow"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.team03weglow.app"
        minSdk = 24
        targetSdk = 37
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "SUPABASE_URL",
            buildString(developmentUrl),
        )

        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            buildString(developmentKey),
        )

    }

    signingConfigs {
        if (uploadSigningConfigured) {
            create("upload") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "WeGlow Dev")
            buildConfigField("String", "ENVIRONMENT", buildString("development"))
            buildConfigField("String", "AUTH_SCHEME", buildString("weglow-dev"))
            manifestPlaceholders["authScheme"] = "weglow-dev"
        }
        create("staging") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            matchingFallbacks += listOf("debug")
            resValue("string", "app_name", "WeGlow Staging")
            buildConfigField("String", "ENVIRONMENT", buildString("staging"))
            buildConfigField("String", "AUTH_SCHEME", buildString("weglow-staging"))
            manifestPlaceholders["authScheme"] = "weglow-staging"
            buildConfigField("String", "SUPABASE_URL", buildString(stagingUrl))
            buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", buildString(stagingKey))
        }
        release {
            isMinifyEnabled = false
            isDebuggable = false
            buildConfigField("String", "ENVIRONMENT", buildString("production"))
            buildConfigField("String", "AUTH_SCHEME", buildString("weglow"))
            manifestPlaceholders["authScheme"] = "weglow"
            buildConfigField("String", "SUPABASE_URL", buildString(releaseUrl))
            buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", buildString(releaseKey))
            if (uploadSigningConfigured) {
                signingConfig = signingConfigs.getByName("upload")
            }

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
        resValues = true
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

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseEnvironment, validateReleaseSigning)
}
tasks.matching { it.name == "preStagingBuild" }.configureEach {
    dependsOn(validateStagingEnvironment)
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
                val relativePath = file.relativeTo(root).invariantSeparatorsPath

                forbiddenImports.forEach { forbidden ->
                    // Android image decoding is the intended ContentResolver boundary.
                    val imageBoundary = forbidden == "android.content.ContentResolver" &&
                        relativePath.startsWith("com/example/weglow/core/image/")
                    if (!imageBoundary && text.contains(forbidden)) {
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
