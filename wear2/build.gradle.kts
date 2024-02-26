/*
 * This file is part of Tack Android.
 *
 * Tack Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Tack Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Tack Android. If not, see http://www.gnu.org/licenses/.
 *
 * Copyright (c) 2020-2024 by Patrick Zedler
 */

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
}

android {
  namespace = "xyz.zedler.patrick.tack"
  compileSdk = 34

  defaultConfig {
    applicationId = "xyz.zedler.patrick.tack"
    minSdk = 26
    targetSdk = 34
    versionCode = 131 // last number is 1 for wear release
    versionName = "4.0.0"
    resourceConfigurations += listOf("en")
  }

  buildTypes {
    release {
      isMinifyEnabled = true
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
    compose = true
  }
  composeOptions {
    kotlinCompilerExtensionVersion = "1.5.1"
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

dependencies {
  implementation(libs.play.services.wearable)
  implementation(platform(libs.compose.bom))
  implementation(libs.preference)
  implementation(libs.ui)
  implementation(libs.ui.tooling.preview)
  implementation(libs.wear.tooling)
  implementation(libs.compose.material)
  implementation(libs.compose.material.icons)
  implementation(libs.compose.foundation)
  implementation(libs.compose.runtime)
  implementation(libs.compose.animation.graphics)
  implementation(libs.compose.navigation)
  implementation(libs.constraintlayout.compose)
  implementation(libs.activity.compose)
  implementation(libs.core.splashscreen)
  debugImplementation(libs.ui.tooling.preview)
  debugImplementation(libs.ui.test.manifest)
  debugImplementation(libs.ui.tooling)
}