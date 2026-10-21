plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.chatapp"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.chatapp"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf(
                    "androidx.**",
                    "kotlin.**",
                    "kotlinx.**"
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Existing screen and layout libraries
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    // Displays the list of chat messages
    implementation("androidx.recyclerview:recyclerview:1.4.0")

    // Room database and Java code generator
    implementation("androidx.room:room-runtime:2.8.5")
    annotationProcessor("androidx.room:room-compiler:2.8.5")

    // Updates the screen when stored messages change
    implementation("androidx.lifecycle:lifecycle-livedata:2.9.4")

    // Loads shared photos into message rows
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Existing testing libraries
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}