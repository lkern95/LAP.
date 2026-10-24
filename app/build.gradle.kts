plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
}

val releaseStoreFile = providers.environmentVariable("LAP_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("LAP_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("LAP_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("LAP_RELEASE_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val hasReleaseSigningValues = releaseSigningValues.all { !it.isNullOrBlank() }
val isolatedTestSuffix = providers.gradleProperty("lapIsolatedTestSuffix").orNull
    ?.takeIf(String::isNotBlank)
    ?.let { ".$it" }
    .orEmpty()

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "de.run.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.run.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 10
        versionName = "0.3.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = if (providers.gradleProperty("lapIsolatedDeviceTest").orNull == "true") {
                ".isolated$isolatedTestSuffix"
            } else ".debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    if (hasReleaseSigningValues) {
        signingConfigs {
            create("localRelease") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
        buildTypes.getByName("release") {
            signingConfig = signingConfigs.getByName("localRelease")
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Checks that local release signing data is configured without displaying secret values."
    doLast {
        val missing = buildList {
            if (releaseStoreFile.isNullOrBlank()) add("LAP_RELEASE_STORE_FILE")
            if (releaseStorePassword.isNullOrBlank()) add("LAP_RELEASE_STORE_PASSWORD")
            if (releaseKeyAlias.isNullOrBlank()) add("LAP_RELEASE_KEY_ALIAS")
            if (releaseKeyPassword.isNullOrBlank()) add("LAP_RELEASE_KEY_PASSWORD")
        }
        if (missing.isNotEmpty()) {
            throw GradleException("Release-Signierung unvollständig. Lokal fehlen: ${missing.joinToString()}. Siehe docs/RELEASE.md.")
        }
        if (!file(requireNotNull(releaseStoreFile)).isFile) {
            throw GradleException("Die konfigurierte lokale Signierdatei ist nicht verfügbar. Prüfe LAP_RELEASE_STORE_FILE.")
        }
        logger.lifecycle("Lokale Release-Signierung ist konfiguriert.")
    }
}

afterEvaluate {
    tasks.named("assembleRelease") {
        dependsOn(verifyReleaseSigning)
    }
    tasks.matching { name == "bundleRelease" || name == "packageRelease" }.configureEach {
        dependsOn(verifyReleaseSigning)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.work.runtime)
    implementation(libs.garmin.fit)
    implementation("com.garmin.connectiq:ciq-companion-app-sdk:${libs.versions.garminCiq.get()}@aar")
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
