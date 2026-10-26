import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.googleServices) // needs composeApp/google-services.json (Android only)
}

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm("desktop") { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

    js {
        outputModuleName.set("composeApp")
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("composeApp")
        browser()
        binaries.executable()
    }

    // Extra shared source sets on top of the default hierarchy:
    //  firebaseMain = targets where GitLive Firebase exists (Android, iOS, JS)
    //  webMain      = JS + Wasm browser code (file download/upload, online/offline events)
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("firebase") {
                withAndroidTarget()
                withIos()
                withJs()
            }
            group("web") {
                withJs()
                withWasmJs()
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.icons.extended)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.coroutines.core)
            implementation(libs.datetime)
            implementation(libs.serialization.json)
            implementation(libs.settings.no.arg)
        }
        getByName("firebaseMain").dependencies {
            implementation(libs.gitlive.auth)
            implementation(libs.gitlive.firestore)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.coroutines.android)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play)
            implementation(libs.googleid)
        }
        getByName("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.coroutines.swing) // Dispatchers.Main
        }
    }
}

android {
    namespace = "com.debaxyz.selfattendance" // keep: R.string.default_web_client_id etc. are generated under this package
    compileSdk = 36
    defaultConfig {
        applicationId = "com.debaxyz.selfattendance"
        minSdk = 24 // set this to your current minSdk
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    
    signingConfigs {
        create("test") {
            storeFile = file("selfattendance-release.jks")
            val testPassword = "test123"
            keyAlias = "selfattendance"
            keyPassword = testPassword
            storePassword = testPassword
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
          //  isZipAlignEnabled = true
           // isPseudoLocalesEnabled = true
            signingConfig = signingConfigs.getByName("test")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("test")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.debaxyz.selfattendance.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "SelfAttendance"
            packageVersion = "1.0.0"
        }
    }
}
