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

package com.ivianuu.injekt.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class InjektIrGenerationExtension(private val project: Project) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        // generate a module for each binding class
        BindingModuleGenerator(pluginContext).visitModuleFragment(moduleFragment, null)

        // generate accessors for each module
        ModuleAccessorGenerator(pluginContext).visitModuleFragment(moduleFragment, null)

        // generate metadata classes in the aggregate package
        AggregateGenerator(pluginContext, project).visitModuleFragment(moduleFragment, null)

        // transform init calls
        InjektInitTransformer(pluginContext).visitModuleFragment(moduleFragment, null)

        // transform binding provider lambdas to classes
        BindingProviderLambdaToClassTransformer(pluginContext).visitModuleFragment(
            moduleFragment,
            null
        )

        // rewrite key overload stub calls to the right calls
        KeyOverloadTransformer(pluginContext).visitModuleFragment(moduleFragment, null)
        // memoize static keyOf calls
        KeyCachingTransformer(pluginContext).visitModuleFragment(moduleFragment, null)
        // rewrite keyOf<String>() -> keyOf(String::class)
        KeyOfTransformer(pluginContext).visitModuleFragment(moduleFragment, null)

        // perform several optimizations
        BindingProviderCachingTransformer(pluginContext).visitModuleFragment(moduleFragment, null)
    }

}