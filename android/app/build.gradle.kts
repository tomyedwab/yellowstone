plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.tomyedwab.yellowstone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tomyedwab.yellowstone"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

// Task to generate Kotlin models from YAML schema
tasks.register("generateKotlinModels") {
    val rootDir = project.rootDir.parentFile
    val schemaFile = File(rootDir, "backend/tasks/schema/types.yml")
    val outputFile = File(project.projectDir, "src/main/java/com/tomyedwab/yellowstone/generated/Types.kt")
    val generatorScript = File(rootDir, "scripts/generate-kotlin-models.sh")

    inputs.file(schemaFile)
    inputs.file(File(rootDir, "scripts/generate-kotlin-models.go"))
    inputs.file(File(rootDir, "scripts/go.mod"))
    outputs.file(outputFile)

    doLast {
        exec {
            workingDir = rootDir
            commandLine("bash", generatorScript.absolutePath, schemaFile.absolutePath, outputFile.absolutePath)
        }
    }
}

// Task to generate Kotlin events from YAML schema
tasks.register("generateKotlinEvents") {
    val rootDir = project.rootDir.parentFile
    val schemaFile = File(rootDir, "backend/tasks/schema/events.yml")
    val outputFile = File(project.projectDir, "src/main/java/com/tomyedwab/yellowstone/generated/Events.kt")
    val generatorScript = File(rootDir, "scripts/generate-kotlin-models.sh")

    inputs.file(schemaFile)
    inputs.file(File(rootDir, "scripts/generate-kotlin-models.go"))
    inputs.file(File(rootDir, "scripts/go.mod"))
    outputs.file(outputFile)

    doLast {
        exec {
            workingDir = rootDir
            commandLine("bash", generatorScript.absolutePath, schemaFile.absolutePath, outputFile.absolutePath)
        }
    }
}

// Task to generate Kotlin routes from YAML schema
tasks.register("generateKotlinRoutes") {
    val rootDir = project.rootDir.parentFile
    val schemaFile = File(rootDir, "backend/tasks/schema/api.yml")
    val outputFile = File(project.projectDir, "src/main/java/com/tomyedwab/yellowstone/generated/ApiRoutes.kt")
    val generatorScript = File(rootDir, "scripts/generate-kotlin-models.sh")

    inputs.file(schemaFile)
    inputs.file(File(rootDir, "scripts/generate-kotlin-models.go"))
    inputs.file(File(rootDir, "scripts/go.mod"))
    outputs.file(outputFile)

    doLast {
        exec {
            workingDir = rootDir
            commandLine("bash", generatorScript.absolutePath, schemaFile.absolutePath, outputFile.absolutePath)
        }
    }
}

// Task to build Go binary and copy to assets
tasks.register("buildGoBinary") {
    doLast {
        val rootDir = project.rootDir.parentFile
        val buildScript = File(rootDir, "scripts/build-go-binary.sh")
        val buildDir = File(rootDir, "build")
        val assetsDir = File(project.projectDir, "src/main/assets/components")

        // Run the Go build script
        exec {
            workingDir = rootDir
            commandLine("bash", buildScript.absolutePath)
        }

        // Copy binary and MD5 to assets
        copy {
            from(File(buildDir, "tasks.zip"))
            from(File(buildDir, "tasks.md5"))
            into(assetsDir)
        }

        println("Go binary and MD5 copied to assets")
    }
}

// Hook into the Android build process
afterEvaluate {
    // Generate models, events, and routes before compilation
    tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }.configureEach {
        dependsOn("generateKotlinModels", "generateKotlinEvents", "generateKotlinRoutes")
    }

    tasks.matching { it.name.startsWith("generate") && it.name.contains("Assets") }.configureEach {
        dependsOn("buildGoBinary")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // HTTP client and JSON parsing
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Timber for logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Encrypted SharedPreferences for secure storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
