/*
 * Copyright 2019 Manuel Wrage
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

package com.ivianuu.injekt

import kotlin.reflect.KClass

@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class Scope

interface HasScope {
    val scope: KClass<out Annotation>
}

fun <T> Binding<T>.asScoped(): Binding<T> = when (this) {
    is LinkedScopedBinding, is UnlinkedScopedBinding -> this
    is LinkedBinding -> LinkedScopedBinding(this)
    else -> UnlinkedScopedBinding(this)
}

private class UnlinkedScopedBinding<T>(private val binding: Binding<T>) : UnlinkedBinding<T>() {
    override fun link(linker: Linker): LinkedBinding<T> =
        LinkedScopedBinding(binding.link(linker))
}

private class LinkedScopedBinding<T>(private val binding: LinkedBinding<T>) : LinkedBinding<T>() {
    private var _value: Any? = this

    override fun invoke(parameters: ParametersDefinition?): T {
        var value = _value
        if (value === this) {
            synchronized(this) {
                value = _value
                if (value === this) {
                    _value = binding(parameters)
                    value = _value
                }
            }
        }

        return value as T
    }
}