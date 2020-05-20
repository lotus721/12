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

package com.ivianuu.injekt.compiler.transform.annotatedclass

import com.ivianuu.injekt.compiler.InjektFqNames
import com.ivianuu.injekt.compiler.InjektNameConventions
import com.ivianuu.injekt.compiler.hasAnnotatedAnnotations
import com.ivianuu.injekt.compiler.transform.AbstractInjektTransformer
import com.ivianuu.injekt.compiler.transform.InjektDeclarationIrBuilder
import com.ivianuu.injekt.compiler.transform.InjektDeclarationStore
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.addChild
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.module
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

class ClassFactoryTransformer(
    context: IrPluginContext,
    private val declarationStore: InjektDeclarationStore
) : AbstractInjektTransformer(context) {

    private val factoriesByClass = mutableMapOf<IrClass, IrClass>()

    override fun visitModuleFragment(declaration: IrModuleFragment): IrModuleFragment {
        val classes = mutableListOf<IrClass>()

        declaration.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitClass(declaration: IrClass): IrStatement {
                if (declaration.descriptor.hasAnnotatedAnnotations(
                        InjektFqNames.Scope, declaration.module
                    ) || declaration.hasAnnotation(InjektFqNames.Transient) ||
                    declaration.constructors
                        .any {
                            it.descriptor.hasAnnotatedAnnotations(
                                InjektFqNames.Scope, declaration.module
                            ) || it.hasAnnotation(InjektFqNames.Transient)
                        }
                ) {
                    classes += declaration
                }
                return super.visitClass(declaration)
            }
        })

        classes.forEach { getFactoryForClass(it) }

        return super.visitModuleFragment(declaration)
    }

    fun getFactoryForClass(clazz: IrClass): IrClass {
        factoriesByClass[clazz]?.let { return it }
        val constructor = clazz.constructors
            .singleOrNull {
                it.descriptor.hasAnnotatedAnnotations(
                    InjektFqNames.Scope, clazz.module
                ) || it.hasAnnotation(InjektFqNames.Transient)
            } ?: clazz.constructors.singleOrNull()
        val factory = InjektDeclarationIrBuilder(pluginContext, clazz.symbol).run {
            factory(
                name = InjektNameConventions.getFactoryNameForClass(
                    clazz.getPackageFragment()!!.fqName,
                    clazz.descriptor.fqNameSafe
                ),
                visibility = clazz.visibility,
                typeParametersContainer = clazz,
                parameters = constructor?.valueParameters
                    ?.map { valueParameter ->
                        InjektDeclarationIrBuilder.FactoryParameter(
                            name = valueParameter.name.asString(),
                            type = valueParameter.type,
                            assisted = valueParameter.hasAnnotation(InjektFqNames.Assisted),
                            requirement = false
                        )
                    } ?: emptyList(),
                membersInjector = declarationStore.getMembersInjectorForClassOrNull(clazz),
                returnType = clazz.defaultType,
                createExpr = { createFunction ->
                    if (clazz.kind == ClassKind.OBJECT) {
                        builder.irGetObject(clazz.symbol)
                    } else {
                        builder.irCall(constructor!!)
                            .apply {
                                constructor.valueParameters.indices
                                    .map { createFunction.valueParameters[it] }
                                    .forEach {
                                        putValueArgument(
                                            it.index,
                                            irGet(it)
                                        )
                                    }
                            }
                    }
                }
            )
        }
        clazz.file.addChild(factory)
        factoriesByClass[clazz] = factory
        return factory
    }

}
