package com.ivianuu.injekt

import kotlin.collections.Set
import kotlin.collections.filter
import kotlin.collections.forEach
import kotlin.collections.hashMapOf
import kotlin.collections.hashSetOf
import kotlin.collections.linkedMapOf
import kotlin.collections.linkedSetOf
import kotlin.collections.set
import kotlin.collections.toSet
import kotlin.reflect.KClass

/**
 * The actual dependency container which provides bindings
 */
class Component @PublishedApi internal constructor() {

    private val dependencies = linkedSetOf<Component>()
    private val scopeNames = hashSetOf<String>()
    private val bindings = linkedMapOf<Key, Binding<*>>()
    private val instances = hashMapOf<Key, Instance<*>>()

    /**
     * Returns a instance of [T] matching the [type], [name] and [parameters]
     */
    fun <T> get(
        type: KClass<*>,
        name: String? = null,
        parameters: ParametersDefinition? = null
    ): T {
        val key = Key.of(type, name)

        val instance = findInstance<T>(key, true)
            ?: throw BindingNotFoundException("${componentName()} Could not find binding for $key")

        return instance.get(this, parameters)
    }

    /**
     * Adds all binding of the [module]
     */
    fun addModule(module: Module) {
        InjektPlugins.logger?.info("${componentName()} load module ${module.bindings.size}")
        module.bindings.forEach { addBinding(it.value) }
    }

    /**
     * Adds the [dependency] as a dependency
     */
    fun addDependency(dependency: Component) {
        synchronized(this) {
            if (!this.dependencies.add(dependency)) {
                error("Already added ${dependency.componentName()} to ${componentName()}")
            }
        }

        InjektPlugins.logger?.info("${componentName()} Add dependency $dependency")
    }

    /**
     * Returns all direct dependencies of this component
     */
    fun getDependencies(): Set<Component> = dependencies

    /**
     * Adds the [scopeName]
     */
    fun addScopeName(scopeName: String) {
        synchronized(this) {
            if (!this.scopeNames.add(scopeName)) {
                error("Scope name $scopeName was already added")
            }
        }
        InjektPlugins.logger?.info("${componentName()} Add scope name $scopeName")
    }

    /**
     * Returns all scope names of this component
     */
    fun getScopeNames(): Set<String> = scopeNames

    /**
     * Whether or not this component contains the [scopeName]
     */
    fun containsScopeName(scopeName: String): Boolean = scopeNames.contains(scopeName)

    /**
     * Returns all [Binding]s added to this component
     */
    fun getBindings(): Set<Binding<*>> = bindings.values.toSet()

    /**
     * Saves the [binding]
     */
    fun addBinding(binding: Binding<*>) {
        addBindingInternal(binding)
    }

    /**
     * Whether or not contains the [binding]
     */
    fun containsBinding(binding: Binding<*>): Boolean = bindings.containsKey(binding.key)

    /**
     * Returns all [Instance]s of this component
     */
    fun getInstances(): Set<Instance<*>> = instances.values.toSet()

    /**
     * Creates all eager instances of this component
     */
    fun createEagerInstances() {
        instances
            .filter { it.value.binding.eager && !it.value.isCreated }
            .forEach {
                InjektPlugins.logger?.info("${componentName()} Create eager instance for ${it.value.binding}")
                it.value.get(this, null)
            }
    }

    private fun <T> findInstance(key: Key, includeFactories: Boolean): Instance<T>? {
        return synchronized(this) {
            var instance = instances[key]

            if (instance != null) return@synchronized instance as Instance<T>

            for (dependency in dependencies) {
                instance = dependency.findInstance<T>(key, false)
                if (instance != null) return@synchronized instance
            }

            // we search for generated factories as a last resort
            if (includeFactories && key is Key.TypeKey) {
                try {
                    val factory = InjektPlugins.factoryFinder.find<T>(key.type)
                        ?: return@findInstance null
                    val binding = factory.create()
                    return@synchronized addBindingInternal(binding) as Instance<T>
                } catch (e: ClassNotFoundException) {
                    // ignore
                }
            }

            return@synchronized null
        }
    }

    private fun <T> createInstance(binding: Binding<T>): Instance<T> {
        val component = if (binding.scopeName != null) {
            findComponentForScope(binding.scopeName)
                ?: error("Cannot create instance for $binding unknown scope ${binding.scopeName}")
        } else {
            null
        }

        return binding.instanceFactory.create(binding, component)
    }

    private fun addBindingInternal(binding: Binding<*>): Instance<*> {
        return synchronized(this) {
            val isOverride = bindings.remove(binding.key) != null

            if (isOverride && !binding.override) {
                throw OverrideException("Try to override binding $binding but was already declared ${binding.key}")
            }

            bindings[binding.key] = binding

            if (binding.scopeName != null && !scopeNames.contains(binding.scopeName)) {
                val parentWithScope = findComponentForScope(binding.scopeName)

                // add the binding to the parent
                if (parentWithScope != null) {
                    return@addBindingInternal parentWithScope.addBindingInternal(binding)
                } else {
                    error("Component scope ${componentName()} does not match binding scope ${binding.scopeName}")
                }
            }

            val instance = createInstance(binding)

            instances[binding.key] = instance

            InjektPlugins.logger?.let { logger ->
                val msg = if (isOverride) {
                    "${componentName()} Override $binding"
                } else {
                    "${componentName()} Declare $binding"
                }
                logger.debug(msg)
            }

            return@synchronized instance
        }
    }

    private fun findComponentForScope(scopeName: String): Component? {
        if (scopeNames.contains(scopeName)) return this
        for (dependency in dependencies) {
            val result = dependency.findComponentForScope(scopeName)
            if (result != null) return result
        }

        return null
    }

}