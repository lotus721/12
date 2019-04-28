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

import com.ivianuu.injekt.component
import com.ivianuu.injekt.definition
import com.ivianuu.injekt.factoryBuilder
import com.ivianuu.injekt.module
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Test

class MapTest {

    @Test
    fun testMapMultiBinding() {
        val component = component {
            module {
                factoryBuilder<String>(NameOne) {
                    definition { "value_one" }
                    bindIntoMap(mapValues, "key_one")
                }
                factoryBuilder<String>(NameTwo) {
                    definition { "value_two" }
                    bindIntoMap(mapValues, "key_two")
                }
                factoryBuilder<String>(NameThree) {
                    definition { "value_three" }
                    bindIntoMap(mapValues, "key_three")
                }
            }
        }

        val map = component.getMap(mapValues)

        assertEquals(3, map.size)
        assertEquals(map["key_one"], "value_one")
        assertEquals(map["key_two"], "value_two")
        assertEquals(map["key_three"], "value_three")

        val lazyMap = component.getLazyMap(mapValues)

        assertEquals(3, lazyMap.size)
        assertEquals(lazyMap.getValue("key_one").value, "value_one")
        assertEquals(lazyMap.getValue("key_two").value, "value_two")
        assertEquals(lazyMap.getValue("key_three").value, "value_three")

        val providerMap = component.getProviderMap(mapValues)

        assertEquals(3, providerMap.size)
        assertEquals(providerMap.getValue("key_one").get(), "value_one")
        assertEquals(providerMap.getValue("key_two").get(), "value_two")
        assertEquals(providerMap.getValue("key_three").get(), "value_three")
    }

    @Test
    fun testOverride() {
        val component = component {
            module {
                factoryBuilder<String>(NameOne) {
                    definition { "value_one" }
                    bindIntoMap(mapValues, "key_one")
                }
                factoryBuilder<String>(NameTwo) {
                    definition { "value_two" }
                    bindIntoMap(mapValues, "key_one", true)
                }
            }
        }

        assertEquals("value_two", component.getMap(mapValues)["key_one"])
    }

    @Test
    fun testAllowValidOverride() {
        val component = component {
            module {
                factoryBuilder<String>(NameOne) {
                    definition { "value_one" }
                    bindIntoMap(mapValues, "key_one")
                }
                factoryBuilder<String>(NameTwo) {
                    definition { "value_two" }
                    bindIntoMap(mapValues, "key_one", true)
                }
            }
        }

        var throwed = false

        try {
            component.getMap(mapValues)
        } catch (e: Exception) {
            throwed = true
        }

        assertFalse(throwed)
    }

    @Test
    fun testDisallowInvalidOverride() {
        val component = component {
            module {
                factoryBuilder<String>(NameOne) {
                    definition { "value_one" }
                    bindIntoMap(mapValues, "key_one")
                }
                factoryBuilder<String>(NameTwo) {
                    definition { "value_two" }
                    bindIntoMap(mapValues, "key_one")
                }
            }
        }

        var throwed = false

        try {
            component.getMap(mapValues)
        } catch (e: Exception) {
            throwed = true
        }

        assertTrue(throwed)
    }

}