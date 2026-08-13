import java.io.File
import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

data class OcrModel(
    val name: String,
    val size: Long,
    val gitBlobSha1: String
)

fun gitBlobSha1(file: File): String {
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update("blob ${file.length()}\u0000".toByteArray(Charsets.UTF_8))
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

val tesseractDataCommit = "e12c65a915945e4c28e237a9b52bc4a8f39a0cec"
val ocrModels = listOf(
    OcrModel("ell.traineddata", 8_945_021L, "f6f73870885f4fd6e63371244c7f3d5904e1b276"),
    OcrModel("eng.traineddata", 15_400_601L, "176dc3220de7db34d3b3aecbfa42043a6038348b")
)
val generatedOcrAssetsDir = layout.buildDirectory.dir("generated/ocrAssets")

val prepareOcrModels = tasks.register("prepareOcrModels") {
    doLast {
        val tessdata = generatedOcrAssetsDir.get().asFile.resolve("tessdata").apply { mkdirs() }
        for (model in ocrModels) {
            val destination = File(tessdata, model.name)
            if (
                destination.isFile &&
                destination.length() == model.size &&
                gitBlobSha1(destination) == model.gitBlobSha1
            ) {
                continue
            }

            val temporary = File(tessdata, ".${model.name}.part")
            temporary.delete()
            val url = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_best/$tesseractDataCommit/${model.name}"
            val connection = URI(url).toURL().openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "PersonalFolder-Gradle")
            }
            try {
                connection.getInputStream().use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
                check(temporary.length() == model.size) {
                    "Unexpected OCR model size for ${model.name}: ${temporary.length()}"
                }
                check(gitBlobSha1(temporary) == model.gitBlobSha1) {
                    "OCR model checksum mismatch for ${model.name}"
                }
                if (destination.exists()) check(destination.delete()) {
                    "Could not replace ${model.name}"
                }
                check(temporary.renameTo(destination)) {
                    "Could not install ${model.name}"
                }
            } finally {
                temporary.delete()
            }
        }
    }
}

val releaseKeystorePath = System.getenv("PERSONAL_FOLDER_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("PERSONAL_FOLDER_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("PERSONAL_FOLDER_KEY_ALIAS")
val releaseKeyPassword = System.getenv("PERSONAL_FOLDER_KEY_PASSWORD")
val releaseSigningInputs = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
)
val hasAnyReleaseSigningInput = releaseSigningInputs.any { !it.isNullOrBlank() }
val hasAllReleaseSigningInputs = releaseSigningInputs.all { !it.isNullOrBlank() }

if (hasAnyReleaseSigningInput && !hasAllReleaseSigningInputs) {
    throw GradleException(
        "Incomplete Personal Folder release signing configuration. " +
            "Provide all PERSONAL_FOLDER_* signing environment variables or none."
    )
}

android {
    namespace = "com.angel.personalfolder"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.angel.personalfolder"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "2.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    sourceSets {
        getByName("main") {
            // Ignore the previously committed broken model files. The build uses only
            // pinned, checksum-verified official Tesseract models generated above.
            assets.setSrcDirs(listOf(generatedOcrAssetsDir.get().asFile))
        }
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    signingConfigs {
        if (hasAllReleaseSigningInputs) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        }
    }

    buildTypes {
        release {
            if (hasAllReleaseSigningInputs) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Intentionally install as a separate app for this OCR-fix build.
            applicationIdSuffix = ".ocrfix"
            versionNameSuffix = "-ocrfix"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) {
        dependsOn(prepareOcrModels)
    }
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    kapt("androidx.room:room-compiler:2.7.0")

    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.4.0")

    implementation("cz.adaptech.tesseract4android:tesseract4android-openmp:4.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("androidx.room:room-testing:2.7.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.room:room-testing:2.7.0")
    androidTestImplementation("androidx.work:work-testing:2.10.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
