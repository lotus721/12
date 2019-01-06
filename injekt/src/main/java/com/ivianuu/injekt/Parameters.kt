package com.ivianuu.injekt

/**
 * Parameters which will be used for assisted injection
 */
@Suppress("UNCHECKED_CAST")
class Parameters(vararg values: Any?) {

    /**
     * All values of this
     */
    val values: List<*> = values.toList()

    private fun <T> elementAt(i: Int): T =
        if (values.size > i) values[i] as T else throw IllegalArgumentException("Can't get parameter value #$i from $this")

    operator fun <T> component1(): T = elementAt(0)
    operator fun <T> component2(): T = elementAt(1)
    operator fun <T> component3(): T = elementAt(2)
    operator fun <T> component4(): T = elementAt(3)
    operator fun <T> component5(): T = elementAt(4)

    /**
     * Number of contained elements
     */
    val size: Int get() = values.size

    /**
     * Returns the element [i]
     */
    operator fun <T> get(i: Int): T = values[i] as T

    /**
     * Returns the first element of [T]
     */
    inline fun <reified T> get(): T = values.first { it is T } as T

}

/**
 * Defines [Parameters]
 */
typealias ParametersDefinition = () -> Parameters

/**
 * Returns new [Parameters] which contains all [values]
 */
fun parametersOf(vararg values: Any?): Parameters = Parameters(*values)

/**
 * Returns new [Parameters] which contains all [values]
 */
fun parametersOf(values: Iterable<Any?>): Parameters =
    Parameters(*values.toList().toTypedArray())

private val emptyParameters = parametersOf()

/**
 * Returns empty [Parameters]
 */
fun emptyParameters(): Parameters = emptyParameters