// injekt-incremental-fix 1615646580025 injekt-end
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

package com.ivianuu.injekt.samples.android.app

import android.app.Application
import com.ivianuu.injekt.component.initializeApp
import com.ivianuu.injekt.component.*
import com.ivianuu.injekt.common.*
import com.ivianuu.injekt.android.*
import com.ivianuu.injekt.samples.android.data.*
import com.ivianuu.injekt.samples.android.domain.*
import com.ivianuu.injekt.samples.android.ui.*

class App : Application() {
    override fun onCreate() {
        // kick start injekt here
        initializeApp()
        super.onCreate()
    }
}