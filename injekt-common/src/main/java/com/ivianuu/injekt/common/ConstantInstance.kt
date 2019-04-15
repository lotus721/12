/*
 * Copyright 2018 Manuel Wrage
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

package com.ivianuu.injekt.common

import com.ivianuu.injekt.Binding
import com.ivianuu.injekt.BindingContext
import com.ivianuu.injekt.Component
import com.ivianuu.injekt.InjektPlugins
import com.ivianuu.injekt.Instance
import com.ivianuu.injekt.Kind
import com.ivianuu.injekt.Module
import com.ivianuu.injekt.ParametersDefinition
import com.ivianuu.injekt.Qualifier
import com.ivianuu.injekt.Scope
import com.ivianuu.injekt.componentName
import com.ivianuu.injekt.logger

/**
 * Constant instance kind
 */
object ConstantKind : Kind {

    private const val INSTANCE_KIND = "Constant"

    override fun <T> createInstance(binding: Binding<T>, component: Component?): Instance<T> =
        ConstantInstance(binding)

    override fun asString(): String = INSTANCE_KIND

}

/**
 * Holds a constant instance
 */
class ConstantInstance<T>(override val binding: Binding<T>) : Instance<T>() {

    override fun get(component: Component, parameters: ParametersDefinition?): T {
        InjektPlugins.logger?.info("${component.componentName()} Return constant $binding")
        return create(component, parameters)
    }

}

/**
 * Provides a constant instance
 */
inline fun <reified T> Module.constant(
    qualifier: Qualifier? = null,
    scope: Scope? = null,
    override: Boolean = false,
    crossinline instance: () -> T
): BindingContext<T> = add(
    Binding(
        type = T::class,
        qualifier = qualifier,
        kind = ConstantKind,
        scope = scope,
        override = override,
        definition = { instance() }
    )
)

/**
 * Adds a [Binding] for the [instance]
 */
fun <T : Any> Component.addConstant(instance: T) {
    addBinding(
        Binding(
            type = instance::class,
            kind = ConstantKind,
            definition = { instance }
        )
    )
}