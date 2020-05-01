package com.ivianuu.injekt.compiler.transform

import com.ivianuu.injekt.compiler.InjektFqNames
import com.ivianuu.injekt.compiler.InjektNameConventions
import com.ivianuu.injekt.compiler.InjektWritableSlices
import com.ivianuu.injekt.compiler.ensureBound
import com.ivianuu.injekt.compiler.irTrace
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.addChild
import org.jetbrains.kotlin.backend.common.ir.copyTypeParametersFrom
import org.jetbrains.kotlin.backend.common.ir.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.types.impl.buildSimpleType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.fqNameForIrSerialization
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

class ModuleTransformer(
    context: IrPluginContext,
    bindingTrace: BindingTrace,
    val declarationStore: InjektDeclarationStore
) : AbstractInjektTransformer(context, bindingTrace) {

    private val moduleFunctions = mutableListOf<IrFunction>()
    private val processedModules = mutableMapOf<IrFunction, IrClass>()
    private val processingModules = mutableSetOf<FqName>()
    private var computedModuleFunctions = false

    override fun visitModuleFragment(declaration: IrModuleFragment): IrModuleFragment {
        computeModuleFunctionsIfNeeded()

        moduleFunctions.forEach { function ->
            DeclarationIrBuilder(context, function.symbol).run {
                getProcessedModule(function)
            }
        }

        return super.visitModuleFragment(declaration)
    }

    fun getProcessedModule(fqName: FqName): IrClass? {
        processedModules.values.firstOrNull {
            it.fqNameForIrSerialization == fqName
        }?.let { return it }

        val function = moduleFunctions.firstOrNull {
            InjektNameConventions.getModuleNameForFunction(it.fqNameForIrSerialization) == fqName
        } ?: return null

        return getProcessedModule(function)
    }

    fun getProcessedModule(function: IrFunction): IrClass? {
        computeModuleFunctionsIfNeeded()

        check(function in moduleFunctions) {
            "Unknown function $function"
        }
        processedModules[function]?.let { return it }
        return DeclarationIrBuilder(context, function.symbol).run {
            val moduleFqName =
                InjektNameConventions.getModuleNameForFunction(function.fqNameForIrSerialization)
            check(moduleFqName !in processingModules) {
                "Circular dependency for module $moduleFqName"
            }
            processingModules += moduleFqName
            val moduleClass = moduleClass(function)
            println(moduleClass.dump())
            function.file.addChild(moduleClass)
            function.body = irExprBody(irInjektIntrinsicUnit())
            processedModules[function] = moduleClass
            processingModules -= moduleFqName
            moduleClass
        }
    }

    private fun computeModuleFunctionsIfNeeded() {
        if (computedModuleFunctions) return
        computedModuleFunctions = true
        moduleFragment.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitFunction(declaration: IrFunction): IrStatement {
                if (declaration.isModule()
                    && (declaration.parent as? IrFunction)?.descriptor?.fqNameSafe?.asString() != "com.ivianuu.injekt.createImplementation"
                    && (declaration.parent as? IrFunction)?.descriptor?.fqNameSafe?.asString() != "com.ivianuu.injekt.createInstance"
                ) {
                    moduleFunctions += declaration
                }

                return super.visitFunction(declaration)
            }
        })
    }

    private fun IrBuilderWithScope.moduleClass(
        function: IrFunction
    ) = buildClass {
        name = InjektNameConventions.getModuleNameForFunction(function.fqNameForIrSerialization)
            .shortName()
    }.apply clazz@{
        createImplicitParameterDeclarationWithWrappedDescriptor()

        copyTypeParametersFrom(function)

        val scopeCalls = mutableListOf<IrCall>()
        val dependencyCalls = mutableListOf<IrCall>()
        val childFactoryCalls = mutableListOf<IrCall>()
        val moduleCalls = mutableListOf<IrCall>()

        val aliasCalls = mutableListOf<IrCall>()
        val transientCalls = mutableListOf<IrCall>()
        val instanceCalls = mutableListOf<IrCall>()
        val scopedCalls = mutableListOf<IrCall>()

        val setCalls = mutableListOf<IrCall>()
        val mapCalls = mutableListOf<IrCall>()

        function.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                super.visitCall(expression)

                val callee = expression.symbol.descriptor

                when {
                    callee.fqNameSafe.asString() == "com.ivianuu.injekt.scope" -> {
                        scopeCalls += expression
                    }
                    callee.fqNameSafe.asString() == "com.ivianuu.injekt.dependency" -> {
                        dependencyCalls += expression
                    }
                    callee.fqNameSafe.asString() == "com.ivianuu.injekt.childFactory" -> {
                        childFactoryCalls += expression
                    }
                    callee.fqNameSafe.asString() == "com.ivianuu.injekt.alias" -> {
                        aliasCalls += expression
                    }
                    callee.fqNameSafe.asString() == "com.ivianuu.injekt.transient" -> {
                        transientCalls += expression
                    }
                    callee.fqNameSafe.asString() == "com.ivianuu.injekt.instance" -> {
                        instanceCalls += expression
                    }
                    callee.fqNameSafe.asString() == "com.ivianuu.injekt.scoped" -> {
                        scopedCalls += expression
                    }
                    callee.fqNameSafe.asString() == "com.ivianuu.injekt.map" -> {
                        mapCalls += expression
                    }
                    callee.fqNameSafe.asString() == "com.ivianuu.injekt.set" -> {
                        setCalls += expression
                    }
                    expression.symbol.descriptor.annotations.hasAnnotation(InjektFqNames.Module) -> {
                        moduleCalls += expression
                    }
                }

                return expression
            }
        })

        addConstructor {
            returnType = defaultType
            isPrimary = true
        }.apply {
            copyTypeParametersFrom(this@clazz)
            body = irBlockBody {
                initializeClassWithAnySuperClass(this@clazz.symbol)
            }
        }

        addChild(
            moduleDescriptor(
                module = this,
                scopeCalls = scopeCalls,
                dependencyCalls = dependencyCalls,
                childFactoryCalls = childFactoryCalls,
                moduleCalls = moduleCalls,
                aliasCalls = aliasCalls,
                transientCalls = transientCalls,
                instanceCalls = instanceCalls,
                scopedCalls = scopedCalls,
                setCalls = setCalls,
                mapCalls = mapCalls
            )
        )
    }

    private fun IrBuilderWithScope.moduleDescriptor(
        module: IrClass,
        scopeCalls: List<IrCall>,
        dependencyCalls: List<IrCall>,
        childFactoryCalls: List<IrCall>,
        moduleCalls: List<IrCall>,
        aliasCalls: List<IrCall>,
        transientCalls: List<IrCall>,
        instanceCalls: List<IrCall>,
        scopedCalls: List<IrCall>,
        setCalls: List<IrCall>,
        mapCalls: List<IrCall>,
    ): IrClass = buildClass {
        kind = ClassKind.INTERFACE
        name = Name.identifier("Descriptor")
    }.apply {
        createImplicitParameterDeclarationWithWrappedDescriptor()

        copyTypeParametersFrom(module)

        annotations += noArgSingleConstructorCall(symbols.astModule)

        // scopes
        scopeCalls.forEachIndexed { index, scopeCall ->
            val scopeType = scopeCall.getTypeArgument(0)!!
            addFunction(
                name = "scope_$index",
                returnType = scopeType,
                modality = Modality.ABSTRACT
            ).apply {
                annotations += noArgSingleConstructorCall(symbols.astScope)
            }
        }

        // dependencies
        dependencyCalls.forEachIndexed { index, dependencyCall ->
            val dependencyType = dependencyCall.getTypeArgument(0)!!
            addFunction(
                name = "dependency_$index",
                returnType = dependencyType,
                modality = Modality.ABSTRACT
            ).apply {
                annotations += noArgSingleConstructorCall(symbols.astScope)
            }
        }

        // child factories
        childFactoryCalls.forEachIndexed { index, childFactoryCall ->
            val functionRef = childFactoryCall.getValueArgument(0)!! as IrFunctionReference
            /*addFunction(
                name = "child_factory_$index",
                returnType = functionRef,
                modality = Modality.ABSTRACT
            ).apply {
                annotations += noArgSingleConstructorCall(symbols.astScope)
            }*/
        }

        // module calls
        moduleCalls.forEachIndexed { index, moduleCall ->
            val moduleClass = declarationStore.getModule(
                InjektNameConventions.getModuleNameForFunction(moduleCall.symbol.descriptor.fqNameSafe)
            )

            addFunction(
                name = "module_$index",
                returnType = moduleClass
                    .typeWith((0 until moduleCall.typeArgumentsCount).map {
                        moduleCall.getTypeArgument(
                            it
                        )!!
                    }),
                modality = Modality.ABSTRACT
            ).apply {
                annotations += noArgSingleConstructorCall(symbols.astModule)
            }
        }

        aliasCalls.forEachIndexed { index, aliasCall ->
            addFunction(
                name = "alias_$index",
                returnType = aliasCall.getTypeArgument(1)!!,
                modality = Modality.ABSTRACT
            ).apply {
                annotations += noArgSingleConstructorCall(symbols.astAlias)
                addValueParameter(
                    name = "original",
                    type = aliasCall.getTypeArgument(0)!!
                )
            }
        }

        (transientCalls + instanceCalls + scopedCalls).forEachIndexed { index, bindingCall ->
            addFunction(
                name = "binding_$index",
                returnType = bindingCall.getTypeArgument(0)!!,
                modality = Modality.ABSTRACT
            ).apply {
                annotations += noArgSingleConstructorCall(symbols.astBinding)

                if (bindingCall !in instanceCalls) {
                    val definition = bindingCall.getValueArgument(0)!! as IrFunctionExpression

                    val assistedParameterCalls = mutableListOf<IrCall>()
                    val bindingDependencyCalls = mutableListOf<IrCall>()

                    definition.function.body?.transformChildrenVoid(object :
                        IrElementTransformerVoid() {
                        override fun visitCall(expression: IrCall): IrExpression {
                            when ((expression.dispatchReceiver as? IrGetValue)?.symbol) {
                                definition.function.extensionReceiverParameter!!.symbol -> {
                                    bindingDependencyCalls += expression
                                }
                                definition.function.valueParameters.single().symbol -> {
                                    assistedParameterCalls += expression
                                }
                            }
                            return super.visitCall(expression)
                        }
                    })

                    (assistedParameterCalls + bindingDependencyCalls).forEachIndexed { index, call ->
                        addValueParameter(
                            name = "p$index",
                            type = call.type
                        ).apply {
                            if (call in assistedParameterCalls) {
                                annotations += noArgSingleConstructorCall(symbols.astAssisted)
                            }
                        }
                    }
                }
            }
        }

        mapCalls.forEachIndexed { mapIndex, mapCall ->
            val mapQualifiers =
                context.irTrace[InjektWritableSlices.QUALIFIERS, mapCall] ?: emptyList()
            val mapKeyType = mapCall.getTypeArgument(0)!!
            val mapValueType = mapCall.getTypeArgument(1)!!

            val mapType = symbolTable.referenceClass(builtIns.map)
                .ensureBound(irProviders)
                .typeWith(mapKeyType, mapValueType)
                .buildSimpleType {
                    mapQualifiers.forEach { qualifier ->
                        annotations += noArgSingleConstructorCall(
                            symbols.getTopLevelClass(qualifier)
                        )
                    }
                }

            addFunction(
                name = "map_$mapIndex",
                returnType = mapType,
                modality = Modality.ABSTRACT
            ).apply {
                annotations += noArgSingleConstructorCall(symbols.astMap)
            }

            val mapBlock = mapCall.getValueArgument(0) as? IrFunctionExpression
            var entryIndex = 0
            mapBlock?.function?.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
                override fun visitCall(expression: IrCall): IrExpression {
                    if (expression.symbol == symbols.mapDsl.functions.single { it.descriptor.name.asString() == "put" }) {
                        addFunction(
                            name = "map_${mapIndex}_entry_${entryIndex++}",
                            returnType = irBuiltIns.unitType,
                            modality = Modality.ABSTRACT
                        ).apply {
                            annotations += noArgSingleConstructorCall(symbols.astMapEntry)
                            addValueParameter(
                                name = "map",
                                type = mapType
                            )
                            addValueParameter(
                                name = "entry",
                                type = expression.getTypeArgument(0)!!
                            ).apply {
                                annotations += irMapKeyConstructorForKey(
                                    expression.getValueArgument(0)!!
                                )
                            }
                        }
                    }

                    return super.visitCall(expression)
                }
            })
        }

        setCalls.forEachIndexed { setIndex, setCall ->
            val setQualifiers =
                context.irTrace[InjektWritableSlices.QUALIFIERS, setCall] ?: emptyList()
            val setElementType = setCall.getTypeArgument(0)!!

            val setType = symbolTable.referenceClass(builtIns.set)
                .ensureBound(irProviders)
                .typeWith(setElementType)
                .buildSimpleType {
                    setQualifiers.forEach { qualifier ->
                        annotations += noArgSingleConstructorCall(
                            symbols.getTopLevelClass(qualifier)
                        )
                    }
                }

            addFunction(
                name = "set_$setIndex",
                returnType = setType,
                modality = Modality.ABSTRACT
            ).apply {
                annotations += noArgSingleConstructorCall(symbols.astSet)
            }

            val setBlock = setCall.getValueArgument(0) as? IrFunctionExpression
            var elementIndex = 0

            setBlock?.function?.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
                override fun visitCall(expression: IrCall): IrExpression {
                    if (expression.symbol == symbols.setDsl.functions.single { it.descriptor.name.asString() == "add" }) {
                        addFunction(
                            name = "set_${setIndex}_element_${elementIndex++}",
                            returnType = irBuiltIns.unitType,
                            modality = Modality.ABSTRACT
                        ).apply {
                            annotations += noArgSingleConstructorCall(symbols.astSetElement)
                            addValueParameter(
                                name = "set",
                                type = setType
                            )
                            addValueParameter(
                                name = "element",
                                type = expression.getTypeArgument(0)!!
                            )
                        }
                    }
                    return super.visitCall(expression)
                }
            })
        }
    }
}
