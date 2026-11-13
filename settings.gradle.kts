/*
 * Copyright (C) 2026 Kingkor Roy Tirtho and Spotube Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

rootProject.name = "Spotube"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Android TV layer. A pure Android library consumed by :composeApp; it never depends on the
// app module (AGP forbids library -> application), so the app installs the TV content through
// TvBridge (see AndroidTV/PLAN.md §3.1).
include(":AndroidTV")

pluginManagement {
    repositories {
        mavenLocal()
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://jitpack.io")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":composeApp")
include(":plugin_interfaces")
include(":js_plugin_example")

// Motor neural local del DJ. Se mantiene como submódulo para conservar su JNI, eSpeak y
// ONNX Runtime en un límite claro; no se envía texto ni audio a un servicio externo.
includeBuild("third_party/piper-kotlin") {
    dependencySubstitution {
        substitute(module("dev.ihorshevchuk.piper:piper-engine")).using(project(":piper-engine"))
        substitute(module("dev.ihorshevchuk.piper:piper-player")).using(project(":piper-player"))
    }
}
