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

package com.ivianuu.injekt.compiler

import com.ivianuu.injekt.Binding
import com.ivianuu.injekt.BindingFactory
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.plusParameter
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName

class FactoryGenerator(private val descriptor: BindingDescriptor) {

    fun generate(): FileSpec {
        val file =
            FileSpec.builder(descriptor.factoryName.packageName, descriptor.factoryName.simpleName)

        val imports = imports()
        if (imports.isNotEmpty()) {
            file.addImport("com.ivianuu.injekt", *imports().toTypedArray())
        }

        file.addType(factory())

        return file.build()
    }

    private fun imports(): Set<String> {
        val imports = mutableSetOf<String>()

        imports.add("create")
        imports.add("attributesOf")

        if (descriptor.constructorParams.any { it.kind == ParamDescriptor.Kind.VALUE }) {
            imports.add("get")
        }

        if (descriptor.constructorParams.any { it.kind == ParamDescriptor.Kind.LAZY }) {
            imports.add("inject")
        }

        if (descriptor.constructorParams.any { it.kind == ParamDescriptor.Kind.PROVIDER }) {
            imports.add("getProvider")
        }

        if (descriptor.constructorParams.any { it.name != null }) {
            imports.add("named")
        }

        return imports
    }

    private fun factory(): TypeSpec {
        return TypeSpec.classBuilder(descriptor.factoryName)
            .addSuperinterface(
                BindingFactory::class.asClassName().plusParameter(descriptor.target)
            )
            .addFunction(
                FunSpec.builder("create")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(Binding::class.asClassName().plusParameter(descriptor.target))
                    .apply {
                        addCode("return %T.create(", Binding::class)
                        addCode("type = %T::class, ", descriptor.target)
                        addCode("kind = %T, ", descriptor.kind.impl)
                        descriptor.scope?.let { addCode("scope = %T, ", it) }
                        addCode(
                            "definition = { params -> %T(",
                            descriptor.target
                        )

                        descriptor.constructorParams.forEachIndexed { i, param ->
                            if (param.paramIndex == -1) {
                                when (param.kind) {
                                    ParamDescriptor.Kind.VALUE -> {
                                        when {
                                            param.qualifier != null -> {
                                                addCode("get(%T)", param.qualifier)
                                            }
                                            param.name != null -> {
                                                addCode("get(named(\"${param.name}\"))")
                                            }
                                            else -> {
                                                addCode("get()")
                                            }
                                        }
                                    }
                                    ParamDescriptor.Kind.LAZY -> {
                                        when {
                                            param.qualifier != null -> {
                                                addCode("inject(%T)", param.qualifier)
                                            }
                                            param.name != null -> {
                                                addCode("inject(named(\"${param.name}\"))")
                                            }
                                            else -> {
                                                addCode("inject()")
                                            }
                                        }
                                    }
                                    ParamDescriptor.Kind.PROVIDER -> {
                                        when {
                                            param.qualifier != null -> {
                                                addCode("getProvider(%T)", param.qualifier)
                                            }
                                            param.name != null -> {
                                                addCode("getProvider(named(\"${param.name}\"))")
                                            }
                                            else -> {
                                                addCode("getProvider()")
                                            }
                                        }
                                    }
                                }
                            } else {
                                addCode("params.get(${param.paramIndex})")
                            }

                            if (i != descriptor.constructorParams.lastIndex) {
                                addCode(", ")
                            }
                        }

                        addCode(")})")

                    }
                    .build()
            )
            .build()
    }

}