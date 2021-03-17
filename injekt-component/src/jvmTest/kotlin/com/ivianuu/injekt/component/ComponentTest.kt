// injekt-incremental-fix 1615647377329 injekt-end
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
import com.ivianuu.injekt.common.typeKeyOf
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.Test

class ComponentTest {

    @Test
    fun testReturnsExistingValue() {
        val component = ComponentBuilder<TestComponent1>(
            elements = { emptySet() },
            initializers = { emptySet() }
        )
            .element { "value" }
            .build()
        component.element<String>() shouldBe "value"
    }

    @Test
    fun testReturnsNullForNotExistingValue() {
        val component = ComponentBuilder<TestComponent1>(
            elements = { emptySet() },
            initializers = { emptySet() }
        ).build()
        component.elementOrNull(typeKeyOf<String>()) shouldBe null
    }

    @Test
    fun testReturnsFromDependency() {
        val component = ComponentBuilder<TestComponent2>(
            elements = { emptySet() },
            initializers = { emptySet() }
        )
            .dependency(
                ComponentBuilder<TestComponent1>(
                    elements = { emptySet() },
                    initializers = { emptySet() }
                )
                    .element { "value" }
                    .build()
            )
            .build()
        component.element<String>() shouldBe "value"
    }

    @Test
fun testGetDependencyReturnsDependency() {
        val dependency = ComponentBuilder<TestComponent1>(
            elements = { emptySet() },
            initializers = { emptySet() }
        ).build()
        val dependent = ComponentBuilder<TestComponent2>(
            elements = { emptySet() },
            initializers = { emptySet() }
        )
            .dependency(dependency)
            .build()
        dependent.element<TestComponent1>() shouldBeSameInstanceAs dependency
    }

    @Test
fun testGetDependencyReturnsNullIfNotExists() {
        val dependent = ComponentBuilder<TestComponent2>(
            elements = { emptySet() },
            initializers = { emptySet() }
        ).build()
        dependent.elementOrNull(typeKeyOf<TestComponent1>()) shouldBe null
    }

    @Test
    fun testOverridesDependency() {
        val component = ComponentBuilder<TestComponent2>(
            elements = { emptySet() },
            initializers = { emptySet() }
        )
            .dependency(
                ComponentBuilder<TestComponent1>(
                    elements = { emptySet() },
                    initializers = { emptySet() }
                )
                    .element { "dependency" }
                    .build()
            )
            .element { "child" }
            .build()
        component.element<String>() shouldBe "child"
    }

    @Test
    fun testInjectedElement() {
        @Given val injected: @ComponentElementBinding<TestComponent1> String = "value"
        val component = ComponentBuilder<TestComponent1>(
            initializers = { emptySet() }
        ).build()
        component.element<String>() shouldBe "value"
    }

    @Test
    fun testElementBinding() {
        @ComponentElementBinding<TestComponent1>
        @Given
        fun element(@Given component: TestComponent1) = component to component
        @ComponentElementBinding<TestComponent2>
        @Given
        fun otherElement() = 0
        val component = ComponentBuilder<TestComponent1>(
            initializers = { emptySet() }
        ).build()
        component.element<Pair<TestComponent1, TestComponent1>>().first shouldBeSameInstanceAs component
        component.elementOrNull(typeKeyOf<Int>()).shouldBeNull()
    }

    @Test
    fun testComponentInitializer() {
        var called = false
        @Given
        fun initializer(@Given component: TestComponent1): ComponentInitializer<TestComponent1> = {
            called = true
        }
        var otherCalled = false
        @Given
        fun otherInitializer(): ComponentInitializer<TestComponent2> = {
            otherCalled = true
        }
        val builder = ComponentBuilder<TestComponent1>(
            elements = { emptySet() }
        )
        called shouldBe false
        val component = builder.build()
        called shouldBe true
        otherCalled shouldBe false
    }

    @Test
    fun testChildComponentModule() {
        @Given
        val childComponentModule = ChildComponentModule1<TestComponent1, String, TestComponent2>()
        val parentComponent = ComponentBuilder<TestComponent1>(
            initializers = { emptySet() }
        ).build()
        val childComponent = parentComponent.element<(String) -> TestComponent2>()("42")
        childComponent.element<String>() shouldBe "42"
    }

}
