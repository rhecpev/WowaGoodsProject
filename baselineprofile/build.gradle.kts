// 베이스라인 프로필 만드는 테스트 모듈.
// 앱을 켜서 자주 쓰는 동작(시작 → 공식 시리즈 → 그리드/리스트 전환 → 마이페이지)을 해 보며, 그때 쓰인 코드 목록을 만든다.
// 만든 목록은 app/src/release/generated/baselineProfiles 에 들어가고, 릴리스 빌드가 그 코드를 미리 컴파일한다.
//
// 만들기: ./gradlew :app:generateBaselineProfile
//   기본은 Gradle 이 직접 띄우는 전용 에뮬레이터(pixel6Api34, 화면 없음)를 쓴다. 사용자 에뮬레이터·기기는 쓰지 않는다.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.example.wowagoodsproject.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Gradle 이 관리하는 전용 에뮬레이터(처음 한 번 시스템 이미지를 받는다)
    testOptions.managedDevices.localDevices {
        create("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "aosp"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

baselineProfile {
    managedDevices += "pixel6Api34"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
