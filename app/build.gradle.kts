plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.razvanalbu.material.not.you.widgets"
    compileSdk = 37

    buildFeatures {
        resValues = true
    }

    sourceSets {
        getByName("main") {
            res.directories.add("src/main/weather")
        }
    }

    defaultConfig {
        applicationId = "com.razvanalbu.material.not.you.widgets"
        minSdk = 31
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"
    }

    signingConfigs {
        create("release").apply {
            val keystorePath = providers.gradleProperty("keystorePath").orNull
            if (keystorePath != null) {
                storeFile = rootProject.file(keystorePath)
                storePassword = providers.gradleProperty("keystorePassword").get()
                keyAlias = providers.gradleProperty("keyAlias").get()
                keyPassword = providers.gradleProperty("keyPassword").get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Material Not You Widgets (Debug)")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val keystorePath = providers.gradleProperty("keystorePath").orNull
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.work.runtime)
}

tasks.register("getVersionName") {
    doLast {
        println(android.defaultConfig.versionName)
    }
}
