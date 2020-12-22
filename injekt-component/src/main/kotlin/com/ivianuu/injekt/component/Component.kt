@file:Suppress("UNCHECKED_CAST")

package com.ivianuu.injekt.component

import com.ivianuu.injekt.Given
import com.ivianuu.injekt.common.ForKey
import com.ivianuu.injekt.common.Key
import com.ivianuu.injekt.common.keyOf

interface Component {
    val key: Key<Component>

    fun <T : Any> getOrNull(key: Key<T>): T?

    fun <T : Any> getScopedValue(key: Int): T?

    fun <T : Any> setScopedValue(key: Int, value: T)

    fun <T : Any> removeScopedValue(key: Int)

    fun dispose()

    interface Disposable {
        fun dispose()
    }

    interface Builder<C : Component> {
        fun <T : Component> dependency(parent: T): Builder<C>
        fun <T : Any> element(key: Key<T>, value: T): Builder<C>
        fun build(): C
    }
}

fun <@ForKey T : Any> Component.get(): T = getOrNull(keyOf())
    ?: error("No value for for $key in ${this.key}")

inline fun <T : Any> Component.scope(key: Int, block: () -> T): T {
    getScopedValue<T>(key)?.let { return it }
    synchronized(this) {
        getScopedValue<T>(key)?.let { return it }
        val value = block()
        setScopedValue(key, value)
        return value
    }
}

inline fun <T : Any> Component.scope(key: Any, block: () -> T): T =
    scope(key.hashCode(), block)

inline fun <@ForKey T : Any> Component.scope(block: () -> T): T =
    scope(keyOf<T>(), block)

@Given fun <@ForKey C : Component> ComponentBuilder(
    @Given injectedElements: (@Given C) -> Set<ComponentElement<C>>,
): Component.Builder<C> = ComponentImpl.Builder(
    keyOf(),
    injectedElements as (Component) -> Set<ComponentElement<*>>
)

fun <C : Component, @ForKey T : Any> Component.Builder<C>.element(value: T) =
    element(keyOf(), value)

typealias ComponentElement<@Suppress("unused") C> = Pair<Key<*>, Any>

fun <@ForKey C : Component, T : Any> componentElement(value: T): ComponentElement<C> =
    keyOf<C>() to value

@PublishedApi internal class ComponentImpl(
    override val key: Key<Component>,
    private val dependencies: List<Component>,
    explicitElements: Map<Key<*>, Any?>,
    injectedElements: (@Given Component) -> Set<ComponentElement<*>>,
) : Component {
    private val elements = explicitElements + injectedElements(this)

    private val scopedValues = mutableMapOf<Int, Any>()

    override fun <T : Any> getOrNull(key: Key<T>): T? {
        if (key == this.key) return this as T
        elements[key]?.let { return it as T }

        for (dependency in dependencies)
            dependency.getOrNull(key)?.let { return it }

        return null
    }

    override fun <T : Any> getScopedValue(key: Int): T? = scopedValues[key] as? T

    override fun <T : Any> setScopedValue(key: Int, value: T) {
        scopedValues[key] = value
    }

    override fun <T : Any> removeScopedValue(key: Int) {
        scopedValues -= key
    }

    override fun dispose() {
        scopedValues.values
            .filterIsInstance<Component.Disposable>()
            .forEach { it.dispose() }
        scopedValues.clear()
    }

    class Builder<C : Component>(
        private val key: Key<Component>,
        private val injectedElements: (Component) -> Set<ComponentElement<*>>,
    ) : Component.Builder<C> {
        private val dependencies = mutableListOf<Component>()
        private val elements = mutableMapOf<Key<*>, Any?>()

        override fun <T : Component> dependency(parent: T): Component.Builder<C> =
            apply { dependencies += parent }

        override fun <T : Any> element(key: Key<T>, value: T): Component.Builder<C> =
            apply {
                elements[key] = value
            }

        override fun build(): C =
            ComponentImpl(key, dependencies, elements, injectedElements) as C
    }
}
