plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.razvanalbu.material.not.you.widgets"
    compileSdk = 37

    sourceSets {
        getByName("main") {
            res.directories.add("src/main/weather")
        }
    }

    defaultConfig {
        applicationId = "com.razvanalbu.material.not.you.widgets"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)

}
