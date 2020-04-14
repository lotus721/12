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

package com.ivianuu.injekt

import junit.framework.Assert.assertEquals
import org.junit.Test

class SetMultiBindingTest {

    @Test
    fun testSetBinding() {
        val component = Component {
            set<Command>(TestQualifier1) {
                add { Command1 }
                add { Command2 }
                add { Command3 }
            }
        }

        val set = component.get<Set<Command>>(qualifier = TestQualifier1)
        assertEquals(3, set.size)
        assertEquals(Command1, set.toList()[0])
        assertEquals(Command2, set.toList()[1])
        assertEquals(Command3, set.toList()[2])

        val providerSet = component.get<Set<Provider<Command>>>(qualifier = TestQualifier1)
        assertEquals(3, providerSet.size)
        assertEquals(Command1, providerSet.toList()[0]())
        assertEquals(Command2, providerSet.toList()[1]())
        assertEquals(Command3, providerSet.toList()[2]())

        val lazySet = component.get<Set<Lazy<Command>>>(qualifier = TestQualifier1)
        assertEquals(3, providerSet.size)
        assertEquals(Command1, lazySet.toList()[0]())
        assertEquals(Command2, lazySet.toList()[1]())
        assertEquals(Command3, lazySet.toList()[2]())
    }

    @Test(expected = IllegalStateException::class)
    fun testThrowsOnNonDeclaredSetBinding() {
        val component = Component()
        component.get<Set<Command>>()
    }

    @Test
    fun testReturnsEmptyOnADeclaredSetBindingWithoutElements() {
        val component = Component {
            set<Command>()
        }

        assertEquals(0, component.get<Set<Command>>().size)
    }

    @Test
    fun testNestedSetBindings() {
        val componentA = Component {
            set<Command> { add { Command1 } }
        }

        val setA = componentA.get<Set<Command>>()
        assertEquals(1, setA.size)
        assertEquals(Command1, setA.toList()[0])

        val componentB = Component {
            parents(componentA)
            set<Command> { add { Command2 } }
        }

        val setB = componentB.get<Set<Command>>()
        assertEquals(2, setB.size)
        assertEquals(Command1, setA.toList()[0])
        assertEquals(Command2, setB.toList()[1])

        val componentC = Component {
            parents(componentB)
            set<Command> { add { Command3 } }
        }

        val setC = componentC.get<Set<Command>>()
        assertEquals(3, setC.size)
        assertEquals(Command1, setA.toList()[0])
        assertEquals(Command2, setB.toList()[1])
        assertEquals(Command3, setC.toList()[2])
    }

    @Test
    fun testOverride() {
        val originalValueComponent = Component {
            set<Command> { add<Command> { Command1 } }
        }
        val overriddenValueComponent = Component {
            parents(originalValueComponent)
            set<Command> {
                add<Command>(duplicateStrategy = DuplicateStrategy.Override) { Command2 }
            }
        }

        assertEquals(Command2, overriddenValueComponent.get<Set<Command>>().single())
    }

    @Test
    fun testOverrideDrop() {
        val originalValueComponent = Component {
            set<Command> { add<Command> { Command1 } }
        }
        val overriddenValueComponent = Component {
            parents(originalValueComponent)
            set<Command> {
                add<Command>(duplicateStrategy = DuplicateStrategy.Drop) { Command2 }
            }
        }

        assertEquals(Command1, overriddenValueComponent.get<Set<Command>>().single())
    }

    @Test(expected = IllegalStateException::class)
    fun testOverrideFail() {
        val originalValueComponent = Component {
            set<Command> { add<Command> { Command1 } }
        }
        val overriddenValueComponent = Component {
            parents(originalValueComponent)
            set<Command> {
                add<Command>(duplicateStrategy = DuplicateStrategy.Fail) { Command2 }
            }
        }
    }

    @Test
    fun testNestedOverride() {
        val componentA = Component {
            set<Command> { add<Command> { Command1 } }
        }
        val componentB = Component {
            parents(componentA)
            set<Command> { add<Command>(duplicateStrategy = DuplicateStrategy.Override) { Command2 } }
        }

        val setA = componentA.get<Set<Command>>()
        assertEquals(Command1, setA.toList()[0])
        val setB = componentB.get<Set<Command>>()
        assertEquals(Command2, setB.toList()[0])
    }

    @Test
    fun testNestedOverrideDrop() {
        val componentA = Component {
            set<Command> { add<Command> { Command1 } }
        }
        val componentB = Component {
            parents(componentA)
            set<Command> { add<Command>(duplicateStrategy = DuplicateStrategy.Drop) { Command2 } }
        }

        val setA = componentA.get<Set<Command>>()
        assertEquals(Command1, setA.toList()[0])
        val setB = componentB.get<Set<Command>>()
        assertEquals(Command1, setB.toList()[0])
    }

    @Test(expected = IllegalStateException::class)
    fun testNestedOverrideFail() {
        val componentA = Component {
            set<Command> { add<Command> { Command1 } }
        }
        val componentB = Component {
            parents(componentA)
            set<Command> { add<Command>(duplicateStrategy = DuplicateStrategy.Fail) { Command2 } }
        }
    }

    @Test
    fun testEagerBoundBindingDependsOnSetOfProvider() {
        Component {
            factory(behavior = Bound + Eager) {
                get<Set<Provider<String>>>()
                    .forEach { it() }
            }
            set<String>()
        }
    }

    @Test
    fun testReusesSetBuildersInsideAComponentBuilder() {
        val component = Component {
            set<Any> { add { Command1 } }
            set<Any> { add { Command2 } }
        }

        assertEquals(2, component.get<Set<Any>>().size)
    }
}
