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

package com.ivianuu.injekt

/**
 * A module is a collection of [Binding]s to drive [Component]s
 */
class Module @PublishedApi internal constructor() {

    internal val bindings = hashMapOf<Key, Binding<*>>()
    internal var mapBindings: MapBindings? = null
        private set
    internal var setBindings: SetBindings? = null
        private set

    fun <T> bind(binding: Binding<T>, key: Key, override: Boolean = false): BindingContext<T> {
        if (bindings.contains(key) && !override) {
            error("Already declared binding for $binding.key")
        }

        binding.override = override

        bindings[key] = binding

        return BindingContext(binding, key, override, this)
    }

    fun include(module: Module) {
        module.bindings.forEach { bind(it.value, it.key, it.value.override) }
        module.mapBindings?.let { nonNullMapBindings().putAll(it) }
        module.setBindings?.let { nonNullSetBindings().addAll(it) }
    }

    fun <K, V> map(
        mapKeyType: Type<K>,
        mapValueType: Type<V>,
        mapName: Any? = null,
        block: (MapBindings.BindingMap<K, V>.() -> Unit)? = null
    ) {
        val mapKey = keyOf(typeOf<Any?>(Map::class, mapKeyType, mapValueType), mapName)
        nonNullMapBindings().get<K, V>(mapKey).apply { block?.invoke(this) }
    }

    fun <E> set(
        setElementType: Type<E>,
        setName: Any? = null,
        block: (SetBindings.BindingSet<E>.() -> Unit)? = null
    ) {
        val setKey = keyOf(typeOf<Any?>(Set::class, setElementType), setName)
        nonNullSetBindings().get<E>(setKey).apply { block?.invoke(this) }
    }

    private fun nonNullMapBindings(): MapBindings =
        mapBindings ?: MapBindings().also { mapBindings = it }

    private fun nonNullSetBindings(): SetBindings =
        setBindings ?: SetBindings().also { setBindings = it }


}

inline fun module(block: Module.() -> Unit): Module = Module().apply(block)

inline fun <reified T> Module.bind(
    binding: Binding<T>,
    name: Any? = null,
    override: Boolean = false
): BindingContext<T> = bind(binding, typeOf(), name, override)

fun <T> Module.bind(
    binding: Binding<T>,
    type: Type<T>,
    name: Any? = null,
    override: Boolean = false
): BindingContext<T> = bind(binding, keyOf(type, name), override)

inline fun <reified T> Module.bind(
    name: Any? = null,
    scoped: Boolean = false,
    override: Boolean = false,
    noinline definition: Definition<T>
): BindingContext<T> = bind(typeOf(), name, scoped, override, definition)

fun <T> Module.bind(
    type: Type<T>,
    name: Any? = null,
    scoped: Boolean = false,
    override: Boolean = false,
    definition: Definition<T>
): BindingContext<T> {
    var binding = definitionBinding(definition)
    if (scoped) binding = binding.asScoped()
    return bind(binding, type, name, override)
}

inline fun <reified T> Module.bindWithState(
    name: Any? = null,
    scoped: Boolean = false,
    override: Boolean = false,
    noinline definition: StateDefinitionFactory.() -> StateDefinition<T>
): BindingContext<T> = bindWithState(typeOf(), name, scoped, override, definition)

fun <T> Module.bindWithState(
    type: Type<T>,
    name: Any? = null,
    scoped: Boolean = false,
    override: Boolean = false,
    definition: StateDefinitionFactory.() -> StateDefinition<T>
): BindingContext<T> {
    var binding = stateDefinitionBinding(definition)
    if (scoped) binding = binding.asScoped()
    return bind(binding, type, name, override)
}

inline fun <reified K, reified V> Module.map(
    mapName: Any? = null,
    noinline block: (MapBindings.BindingMap<K, V>.() -> Unit)? = null
) {
    map(typeOf(), typeOf(), mapName, block)
}

inline fun <reified E> Module.set(
    setName: Any? = null,
    noinline block: (SetBindings.BindingSet<E>.() -> Unit)? = null
) {
    set(typeOf(), setName, block)
}