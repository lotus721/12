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

package com.ivianuu.injekt.android

import androidx.activity.ComponentActivity
import com.ivianuu.injekt.Given
import com.ivianuu.injekt.GivenSetElement
import com.ivianuu.injekt.component.AppComponent
import com.ivianuu.injekt.component.Component
import com.ivianuu.injekt.component.ComponentElementBinding
import com.ivianuu.injekt.component.get

typealias ActivityRetainedComponent = Component

@Given val @Given ComponentActivity.activityRetainedComponent: ActivityRetainedComponent
    get() = viewModelStore.component {
        application.appComponent.get<() -> ActivityRetainedComponent>()()
    }

@ComponentElementBinding<AppComponent>
@Given
fun activityRetainedComponentFactory(
    @Given parent: AppComponent,
    @Given builderFactory: () -> Component.Builder<ActivityRetainedComponent>,
): () -> ActivityRetainedComponent = { builderFactory().dependency(parent).build() }

@Given val @Given ActivityRetainedComponent.appComponentFromRetained: AppComponent
    get() = get()
