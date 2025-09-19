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
    }
}

// Task to build Go binary and copy to assets
tasks.register("buildGoBinary") {
    doLast {
        val rootDir = project.rootDir.parentFile
        val buildScript = File(rootDir, "scripts/build-go-binary.sh")
        val buildDir = File(rootDir, "build")
        val assetsDir = File(project.projectDir, "src/main/assets")

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

    // Encrypted SharedPreferences for secure storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
