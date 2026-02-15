plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    // ❌ Crashlytics removed for now (not needed, can re-add later)
}

android {
    namespace = "com.example.smartcheckin"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.smartcheckin"
        minSdk = 24
        targetSdk = 34
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
}

dependencies {

    /* ================= ANDROIDX / UI ================= */
    implementation(libs.appcompat)
    implementation(libs.material)

    /* ================= LOCATION ================= */
    implementation(libs.play.services.location)

    /* ================= MAP (OSMDROID ONLY) ================= */
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    /* ================= FIREBASE (CORRECT & SAFE) ================= */

    // ✅ Firebase BOM (SINGLE SOURCE OF TRUTH)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))

    // ✅ Firebase SDKs (NO versions!)
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")

    // ✅ REQUIRED by Firestore (CRASH FIX)
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation(libs.play.services.maps)

    /* ================= TESTING ================= */
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
