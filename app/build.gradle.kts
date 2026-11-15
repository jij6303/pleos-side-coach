import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jij6303.sidecoach"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.jij6303.sidecoach"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    // release 서명: 루트의 keystore.properties(커밋 금지)가 있을 때만 서명한다
    val keystoreFile = rootProject.file("keystore.properties")
    val releaseSigning = if (keystoreFile.exists()) {
        val props = Properties().apply { keystoreFile.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = rootProject.file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    } else null

    buildTypes {
        release {
            signingConfig = releaseSigning
            // 프로젝트 템플릿의 R8 축소(packageScope)는 실행 시 IllegalAccessError로 크래시해 끈다
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // AAOS Car API (CarPropertyManager: 기어, 차속, 비상등, 주차 브레이크)
    useLibrary("android.car")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json) // Android의 org.json은 JVM 테스트에서 동작하지 않아 실제 구현을 넣는다
    debugImplementation(libs.androidx.compose.ui.tooling)
}