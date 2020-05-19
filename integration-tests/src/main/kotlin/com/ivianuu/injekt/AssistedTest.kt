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

import org.junit.Test

class AssistedTest {

    @Test
    fun testAssistedWithAnnotations() = codegen(
        """
        @Transient
        class Dep(
            @Assisted val assisted: String,
            val foo: Foo
        )
        
        @InstanceFactory
        fun createDep(): @Provider (String) -> Dep {
            instance(Foo())
            return create()
        }
    """
    ) {
        assertOk()
    }

    @Test
    fun testAssistedInDsl() = codegen(
        """
        class Dep(val assisted: String, val foo: Foo)
        
        @InstanceFactory
        fun createDep(): @Provider (String) -> Dep {
            transient { Foo() }
            transient { (assisted: String) -> Dep(assisted, get()) }
            return create()
        }
    """
    ) {
        assertOk()
    }

    @Test
    fun testAssistedInDsl2() = codegen(
        """
        class Dep(val assisted: String, val foo: Foo)
        
        @InstanceFactory
        fun createDep(): @Provider (String) -> Dep {
            transient { Foo() }
            transient { Dep(it.component1(), get()) }
            return create()
        }
    """
    ) {
        assertOk()
    }

}