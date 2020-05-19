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

package com.ivianuu.injekt.sample

import android.app.Application
import com.ivianuu.injekt.ApplicationComponent
import com.ivianuu.injekt.ApplicationScoped
import com.ivianuu.injekt.Module
import com.ivianuu.injekt.Qualifier
import com.ivianuu.injekt.composition.installIn
import com.ivianuu.injekt.transient
import java.io.File

@Module
fun dataModule() {
    installIn<ApplicationComponent>()
    @DatabaseFile
    transient { get<Application>().cacheDir!! }
}

@Target(AnnotationTarget.EXPRESSION, AnnotationTarget.TYPE)
@Qualifier
annotation class DatabaseFile

@ApplicationScoped
class Database(private val file: @DatabaseFile File)

@ApplicationScoped
class Repo(private val database: Database, private val api: Api) {
    fun refresh() {
    }
}

@ApplicationScoped
class Api