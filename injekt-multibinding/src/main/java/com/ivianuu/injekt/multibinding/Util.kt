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

package com.ivianuu.injekt.multibinding

import com.ivianuu.injekt.*
import kotlin.collections.set

const val KEY_ORIGINAL_KEY = "original_key"

internal fun <K, V> Component.getMultiBindingMap(mapName: Name): Map<K, Binding<V>> {
    val allMapBindings = getAllBindings()
        .mapNotNull { binding ->
            binding.attributes.get<Map<Name, MapBinding>>(KEY_MAP_BINDINGS)
                ?.get(mapName)?.let { binding to it }
        }

    val mapBindingsToUse = linkedMapOf<Any, Binding<*>>()

    // check overrides
    allMapBindings.forEach { (binding, mapBinding) ->
        val isOverride = mapBindingsToUse.remove(mapBinding.key) != null

        if (isOverride && !mapBinding.override) {
            throw OverrideException("Try to override ${mapBinding.key} in map binding $mapBinding")
        }

        mapBindingsToUse[mapBinding.key] = binding
    }

    return mapBindingsToUse as Map<K, Binding<V>>
}

internal fun <V> Component.getMultiBindingSet(setName: Name): Set<Binding<V>> {
    val allSetBindings = getAllBindings()
        .mapNotNull { binding ->
            binding.attributes.get<Map<Name, SetBinding>>(KEY_SET_BINDINGS)
                ?.get(setName)?.let { binding to it }
        }

    val setBindingsToUse = linkedMapOf<Key, Binding<*>>()

    // check overrides
    allSetBindings.forEach { (binding, setBinding) ->
        val key = binding.attributes.getOrDefault(KEY_ORIGINAL_KEY) { binding.key }

        val isOverride = setBindingsToUse.remove(binding.key) != null

        if (isOverride && !setBinding.override) {
            throw OverrideException("Try to override $key in set binding $setBinding")
        }

        setBindingsToUse[binding.key] = binding
    }

    return setBindingsToUse.values.toSet() as Set<Binding<V>>
}

internal fun Component.getAllBindings(): Set<Binding<*>> =
    linkedSetOf<Binding<*>>().also { collectBindings(it) }

internal fun Component.collectBindings(
    bindings: MutableSet<Binding<*>>
) {
    getDependencies().forEach { it.collectBindings(bindings) }
    bindings.addAll(getBindings())
}