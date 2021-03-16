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

package com.ivianuu.injekt.component

import com.ivianuu.injekt.Given
import com.ivianuu.injekt.Qualifier
import com.ivianuu.injekt.common.Scoped

/**
 * Converts a [@Eager<C> T] to a [T] which is scoped to the lifecycle of [C] and will be instantiated
 * as soon as the hosting [Component] get's initialized
 */
@Qualifier
annotation class Eager<C : Component>

@Given
fun <@Given T : @Eager<U> S, S : Any, U : Component> eagerImpl() = EagerModule<T, S, U>()

class EagerModule<T : S, S : Any, U : Component> {

    @Scoped<U>
    @Given
    inline fun scopedInstance(@Given instance: T): S = instance

    @Given
    fun initializer(@Given factory: () -> S): ComponentInitializer<U> = {
        factory()
    }

}