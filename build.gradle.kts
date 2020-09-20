import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile

/*
 * Copyright 2020 Manuel Wrage
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

buildscript {
    repositories {
        mavenLocal()
        maven("https://dl.bintray.com/ivianuu/maven")
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
        google()
        jcenter()
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://plugins.gradle.org/m2")
    }
    dependencies {
        classpath(Deps.androidGradlePlugin)
        classpath(Deps.bintrayGradlePlugin)
        classpath(Deps.buildConfigGradlePlugin)
        classpath(Deps.Kotlin.gradlePlugin)
        classpath(Deps.mavenGradlePlugin)
        classpath(Deps.spotlessGradlePlugin)
    }
}

allprojects {
    // todo remove
    configurations.all {
        resolutionStrategy.force("com.squareup:kotlinpoet:1.5.0")
    }

    repositories {
        mavenLocal()
        maven("https://dl.bintray.com/ivianuu/maven")
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
        google()
        jcenter()
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://plugins.gradle.org/m2")
    }

    val baseSrcDir = buildDir.resolve("generated/source/injekt")
    val cacheDir = buildDir.resolve("injekt/cache")
    // todo move
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        val compilation = AbstractKotlinCompile::class.java
            .getDeclaredMethod("getTaskData\$kotlin_gradle_plugin")
            .invoke(this)
            .let { taskData ->
                taskData.javaClass
                    .getDeclaredMethod("getCompilation")
                    .invoke(taskData) as KotlinCompilation<*>
            }
        val androidVariantData: com.android.build.gradle.api.BaseVariant? =
            (compilation as? org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation)?.androidVariant

        val sourceSetName = androidVariantData?.name ?: compilation.compilationName

        val resourcesDir = (if (androidVariantData != null) {
            buildDir.resolve("tmp/kotlin-classes/$sourceSetName")
        } else {
            compilation.output.resourcesDir
        }).also { it.mkdirs() }.absolutePath

        kotlinOptions {
            useIR = true
            freeCompilerArgs += listOf(
                "-P", "plugin:com.ivianuu.injekt:srcDir=${
                    baseSrcDir.resolve(sourceSetName)
                        .also { it.mkdirs() }.absolutePath
                }",
                "-P", "plugin:com.ivianuu.injekt:resourcesDir=$resourcesDir",
                "-P", "plugin:com.ivianuu.injekt:cacheDir=${
                    cacheDir.resolve(sourceSetName)
                        .also { it.mkdirs() }.absolutePath
                }"
            )
        }
    }
}