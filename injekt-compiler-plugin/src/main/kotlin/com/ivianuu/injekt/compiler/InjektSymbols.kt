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

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.descriptors.findTypeAliasAcrossModuleDependencies
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeAliasSymbol
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class InjektSymbols(val pluginContext: IrPluginContext) {

    val injektPackage = getPackage(InjektFqNames.InjektPackage)
    val internalPackage = getPackage(InjektFqNames.InternalPackage)

    val injektAst = pluginContext.referenceClass(InjektFqNames.InjektAst)!!

    private fun IrClassSymbol.childClass(name: Name) = owner.declarations
        .filterIsInstance<IrClass>()
        .singleOrNull { it.name == name }
        ?.symbol ?: error("Couldn't find $name in ${owner.dump()}")

    val astAlias = injektAst.childClass(InjektFqNames.AstAlias.shortName())
    val astAssisted = pluginContext.referenceClass(InjektFqNames.AstAssisted)!!
    val astBinding = injektAst.childClass(InjektFqNames.AstBinding.shortName())
    val astChildFactory = injektAst.childClass(InjektFqNames.AstChildFactory.shortName())
    val astDependency = injektAst.childClass(InjektFqNames.AstDependency.shortName())
    val astEntryPoints = injektAst.childClass(InjektFqNames.AstEntryPoints.shortName())
    val astInline = injektAst.childClass(InjektFqNames.AstInline.shortName())
    val astMap = injektAst.childClass(InjektFqNames.AstMap.shortName())
    val astMapEntry = astMap.childClass(InjektFqNames.AstMapEntry.shortName())
    val astMapClassKey = astMap.childClass(InjektFqNames.AstMapClassKey.shortName())
    val astMapTypeParameterClassKey =
        astMap.childClass(InjektFqNames.AstMapTypeParameterClassKey.shortName())
    val astMapIntKey = astMap.childClass(InjektFqNames.AstMapIntKey.shortName())
    val astMapLongKey = astMap.childClass(InjektFqNames.AstMapLongKey.shortName())
    val astMapStringKey = astMap.childClass(InjektFqNames.AstMapStringKey.shortName())
    val astModule = injektAst.childClass(InjektFqNames.AstModule.shortName())
    val astName = injektAst.childClass(InjektFqNames.AstName.shortName())
    val astObjectGraphFunction =
        injektAst.childClass(InjektFqNames.AstObjectGraphFunction.shortName())
    val astPath = injektAst.childClass(InjektFqNames.AstPath.shortName())
    val astClassPath = astPath.childClass(InjektFqNames.AstClassPath.shortName())
    val astPropertyPath = astPath.childClass(InjektFqNames.AstPropertyPath.shortName())
    val astTypeParameterPath = astPath.childClass(InjektFqNames.AstTypeParameterPath.shortName())
    val astValueParameterPath = astPath.childClass(InjektFqNames.AstValueParameterPath.shortName())
    val astParents = injektAst.childClass(InjektFqNames.AstParents.shortName())
    val astScope = injektAst.childClass(InjektFqNames.AstScope.shortName())
    val astScoped = injektAst.childClass(InjektFqNames.AstScoped.shortName())
    val astSet = injektAst.childClass(InjektFqNames.AstSet.shortName())
    val astSetElement = astSet.childClass(InjektFqNames.AstSetElement.shortName())
    val astTyped = injektAst.childClass(InjektFqNames.AstTyped.shortName())

    val assisted = pluginContext.referenceClass(InjektFqNames.Assisted)!!
    val assistedParameters = pluginContext.referenceClass(InjektFqNames.AssistedParameters)!!

    val childFactory = pluginContext.referenceClass(InjektFqNames.ChildFactory)!!

    val doubleCheck = pluginContext.referenceClass(InjektFqNames.DoubleCheck)!!
    val factory = pluginContext.referenceClass(InjektFqNames.Factory)!!

    val injectProperty = pluginContext.referenceClass(InjektFqNames.InjectProperty)!!

    val instanceFactory = pluginContext.referenceClass(InjektFqNames.InstanceFactory)!!

    val lazy = pluginContext.referenceClass(InjektFqNames.Lazy)!!

    val mapDsl = pluginContext.referenceClass(InjektFqNames.MapDsl)!!
    val mapOfValueFactory = pluginContext.referenceClass(InjektFqNames.MapOfValueFactory)!!
    val mapOfProviderFactory = pluginContext.referenceClass(InjektFqNames.MapOfProviderFactory)!!
    val mapOfLazyFactory = pluginContext.referenceClass(InjektFqNames.MapOfLazyFactory)!!

    val module = pluginContext.referenceClass(InjektFqNames.Module)!!

    val noOpMembersInjector = pluginContext.referenceClass(InjektFqNames.NoOpMembersInjector)!!

    val provider = pluginContext.referenceClass(InjektFqNames.Provider)!!
    val providerDefinition = getTypeAlias(InjektFqNames.ProviderDefinition)
    val providerDsl = pluginContext.referenceClass(InjektFqNames.ProviderDsl)!!
    val providerOfLazy = pluginContext.referenceClass(InjektFqNames.ProviderOfLazy)!!

    val setDsl = pluginContext.referenceClass(InjektFqNames.SetDsl)!!
    val setOfValueFactory = pluginContext.referenceClass(InjektFqNames.SetOfValueFactory)!!
    val setOfProviderFactory = pluginContext.referenceClass(InjektFqNames.SetOfProviderFactory)!!
    val setOfLazyFactory = pluginContext.referenceClass(InjektFqNames.SetOfLazyFactory)!!

    val singleInstanceFactory = pluginContext.referenceClass(InjektFqNames.SingleInstanceFactory)!!

    val transient = pluginContext.referenceClass(InjektFqNames.Transient)!!

    val uninitialized = pluginContext.referenceClass(InjektFqNames.Uninitialized)!!

    fun getTypeAlias(fqName: FqName): IrTypeAliasSymbol =
        pluginContext.symbolTable.referenceTypeAlias(
            pluginContext.moduleDescriptor.findTypeAliasAcrossModuleDependencies(
                ClassId.topLevel(
                    fqName
                )
            )
                ?: error("No class found for $fqName")
        )

    fun getPackage(fqName: FqName): PackageViewDescriptor =
        pluginContext.moduleDescriptor.getPackage(fqName)
}