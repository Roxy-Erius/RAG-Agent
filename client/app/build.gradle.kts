plugins {
    id("com.android.application")
}

android {
    namespace = "com.ragagent"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ragagent"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // 后端地址 — 从 local.properties 读取（不提交到 Git），每人自己配
        // 模拟器默认 http://10.0.2.2:8080，真机 USB 用局域网 IP
        val localFile = rootProject.file("local.properties")
        val baseUrl = if (localFile.exists()) {
            localFile.readLines()
                .firstOrNull { it.startsWith("base.url=") }
                ?.substringAfter("base.url=")
                ?.trim() ?: "http://10.0.2.2:8080"
        } else "http://10.0.2.2:8080"
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
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
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // OkHttp + SSE
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
