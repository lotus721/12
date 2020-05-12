package com.ivianuu.injekt.compiler.transform.module

import com.ivianuu.injekt.compiler.ClassPath
import com.ivianuu.injekt.compiler.InjektFqNames
import com.ivianuu.injekt.compiler.InjektSymbols
import com.ivianuu.injekt.compiler.InjektWritableSlices
import com.ivianuu.injekt.compiler.Path
import com.ivianuu.injekt.compiler.PropertyPath
import com.ivianuu.injekt.compiler.TypeParameterPath
import com.ivianuu.injekt.compiler.ValueParameterPath
import com.ivianuu.injekt.compiler.classOrFail
import com.ivianuu.injekt.compiler.ensureBound
import com.ivianuu.injekt.compiler.hasAnnotation
import com.ivianuu.injekt.compiler.irTrace
import com.ivianuu.injekt.compiler.transform.InjektDeclarationIrBuilder
import com.ivianuu.injekt.compiler.transform.InjektDeclarationStore
import com.ivianuu.injekt.compiler.typeArguments
import com.ivianuu.injekt.compiler.withAnnotations
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.addChild
import org.jetbrains.kotlin.backend.common.ir.allParameters
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.toKotlinType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.getArgumentsWithIr
import org.jetbrains.kotlin.ir.util.isFunction
import org.jetbrains.kotlin.ir.util.nameForIrSerialization
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter

class ModuleDeclarationFactory(
    private val module: ModuleImpl,
    private val pluginContext: IrPluginContext,
    private val symbols: InjektSymbols,
    private val nameProvider: NameProvider,
    private val declarationStore: InjektDeclarationStore,
    private val providerFactory: ModuleProviderFactory
) {

    private val moduleClass get() = module.clazz

    fun create(call: IrCall): List<ModuleDeclaration> {
        val callee = call.symbol.descriptor

        val declarations = mutableListOf<ModuleDeclaration>()

        when {
            callee.fqNameSafe.asString() == "com.ivianuu.injekt.scope" ->
                declarations += createScopeDeclaration(call)
            callee.fqNameSafe.asString() == "com.ivianuu.injekt.dependency" ->
                declarations += createDependencyDeclaration(call)
            callee.fqNameSafe.asString() == "com.ivianuu.injekt.childFactory" ->
                declarations += createChildFactoryDeclaration(call)
            callee.fqNameSafe.asString() == "com.ivianuu.injekt.alias" ->
                declarations += createAliasDeclaration(call)
            callee.fqNameSafe.asString() == "com.ivianuu.injekt.transient" ||
                    callee.fqNameSafe.asString() == "com.ivianuu.injekt.scoped" ||
                    callee.fqNameSafe.asString() == "com.ivianuu.injekt.instance" ->
                declarations += createBindingDeclaration(call)
            callee.fqNameSafe.asString() == "com.ivianuu.injekt.map" ->
                declarations += createMapDeclarations(call)
            callee.fqNameSafe.asString() == "com.ivianuu.injekt.set" ->
                declarations += createSetDeclarations(call)
            call.symbol.owner.hasAnnotation(InjektFqNames.Module) ||
                    call.isModuleLambdaInvoke() ->
                declarations += createIncludedModuleDeclarations(call)
        }

        return declarations
    }

    private fun createScopeDeclaration(call: IrCall): ScopeDeclaration =
        ScopeDeclaration(call.getTypeArgument(0)!!)

    private fun createDependencyDeclaration(call: IrCall): DependencyDeclaration {
        val dependencyType = call.getTypeArgument(0)!!

        val property = InjektDeclarationIrBuilder(pluginContext, moduleClass.symbol)
            .fieldBakedProperty(
                moduleClass,
                Name.identifier(nameProvider.allocate("dependency")),
                dependencyType
            )

        val path = PropertyPath(property)

        return DependencyDeclaration(dependencyType, path) {
            irSetField(
                it(),
                property.backingField!!,
                call.getValueArgument(0)!!
            )
        }
    }

    private fun createChildFactoryDeclaration(call: IrCall): ChildFactoryDeclaration {
        val factoryRef = call.getValueArgument(0)!! as IrFunctionReference
        val factoryModuleClass = declarationStore.getModuleClassForFunctionOrNull(
            declarationStore.getModuleFunctionForFactory(factoryRef.symbol.owner)
        )
        return ChildFactoryDeclaration(factoryRef, factoryModuleClass)
    }

    private fun createAliasDeclaration(call: IrCall): AliasDeclaration =
        AliasDeclaration(call.getTypeArgument(0)!!, call.getTypeArgument(1)!!)

    private fun createBindingDeclaration(call: IrCall): BindingDeclaration {
        val bindingQualifiers =
            pluginContext.irTrace[InjektWritableSlices.QUALIFIERS, call] ?: emptyList()
        val bindingType = call.getTypeArgument(0)!!
            .withAnnotations(bindingQualifiers)
        return createBindingDeclarationFromSingleArgument(
            bindingType,
            if (call.valueArgumentsCount != 0) call.getValueArgument(0) else null,
            call.symbol.owner.name.asString() == "instance",
            call.symbol.owner.name.asString() == "scoped"
        )
    }

    private fun createMapDeclarations(call: IrCall): List<ModuleDeclaration> {
        val declarations = mutableListOf<ModuleDeclaration>()
        val mapQualifiers =
            pluginContext.irTrace[InjektWritableSlices.QUALIFIERS, call] ?: emptyList()
        val mapKeyType = call.getTypeArgument(0)!!
        val mapValueType = call.getTypeArgument(1)!!

        val mapType = pluginContext.symbolTable.referenceClass(pluginContext.builtIns.map)
            .ensureBound(pluginContext.irProviders)
            .typeWith(mapKeyType, mapValueType)
            .withAnnotations(mapQualifiers)

        declarations += MapDeclaration(mapType)

        val mapBlock = call.getValueArgument(0) as? IrFunctionExpression
        mapBlock?.function?.body?.transformChildrenVoid(object :
            IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                if (expression.symbol == symbols.mapDsl.functions.single { it.descriptor.name.asString() == "put" }) {
                    declarations += MapEntryDeclaration(
                        mapType,
                        expression.getValueArgument(0)!!,
                        expression.getTypeArgument(0)!!
                    )
                }

                return super.visitCall(expression)
            }
        })

        return declarations
    }

    private fun createSetDeclarations(call: IrCall): List<ModuleDeclaration> {
        val declarations = mutableListOf<ModuleDeclaration>()
        val setQualifiers =
            pluginContext.irTrace[InjektWritableSlices.QUALIFIERS, call] ?: emptyList()
        val setElementType = call.getTypeArgument(0)!!

        val setType = pluginContext.symbolTable.referenceClass(pluginContext.builtIns.set)
            .ensureBound(pluginContext.irProviders)
            .typeWith(setElementType)
            .withAnnotations(setQualifiers)

        declarations += SetDeclaration(setType)

        val setBlock = call.getValueArgument(0) as? IrFunctionExpression
        setBlock?.function?.body?.transformChildrenVoid(object :
            IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                if (expression.symbol == symbols.setDsl.functions.single { it.descriptor.name.asString() == "add" }) {
                    declarations += SetElementDeclaration(
                        setType,
                        expression.getTypeArgument(0)!!
                    )
                }
                return super.visitCall(expression)
            }
        })

        return declarations
    }

    private fun IrCall.isModuleLambdaInvoke(): Boolean {
        return origin == IrStatementOrigin.INVOKE &&
                dispatchReceiver?.type?.hasAnnotation(InjektFqNames.Module) == true
    }

    private fun createIncludedModuleDeclarations(call: IrCall): List<ModuleDeclaration> {
        val declarations = mutableListOf<ModuleDeclaration>()

        if (call.isModuleLambdaInvoke()) {
            val includeName = nameProvider.allocate("include")
            val arguments = call.getArgumentsWithIr().drop(1)
            val moduleValueParameterProperties = arguments
                .mapIndexed { index, (valueParameter, _) ->
                    InjektDeclarationIrBuilder(pluginContext, moduleClass.symbol)
                        .fieldBakedProperty(
                            moduleClass,
                            Name.identifier("${includeName}\$p$index"),
                            valueParameter.type
                        )
                }
            declarations += IncludedModuleDeclaration(
                pluginContext.irBuiltIns.unitType,
                true,
                when (val dispatchReceiver = call.dispatchReceiver) {
                    is IrGetValue -> ValueParameterPath(dispatchReceiver.symbol.owner as IrValueParameter)
                    else -> error("Unexpected lambda expression ${call.dump()}")
                },
                moduleValueParameterProperties
                    .map {
                        IncludedModuleDeclaration.Parameter(
                            PropertyPath(it),
                            it.getter!!.returnType
                        )
                    }
            ) { moduleExpression ->
                irBlock {
                    arguments
                        .forEachIndexed { index, (_, expression) ->
                            +irSetField(
                                moduleExpression(),
                                moduleValueParameterProperties[index].backingField!!,
                                expression
                            )
                        }
                }
            }

            return declarations
        }

        includeModuleFromFunction(
            call.symbol.owner,
            (0 until call.typeArgumentsCount).map { call.getTypeArgument(it)!! },
            call.getArgumentsWithIr().map { it.first to { it.second } },
            declarations
        )

        return declarations
    }

    private fun includeModuleFromFunction(
        function: IrFunction,
        typeArguments: List<IrType>,
        valueArguments: List<Pair<IrValueParameter, () -> IrExpression>>,
        declarations: MutableList<ModuleDeclaration>
    ) {
        val includedClass = declarationStore.getModuleClassForFunction(function)
        val includedType = includedClass.typeWith(typeArguments)
        val includedDescriptor = includedClass
            .declarations.single {
                it is IrClass && it.nameForIrSerialization.asString() == "Descriptor"
            }
            .let { it as IrClass }
        val property = InjektDeclarationIrBuilder(pluginContext, includedClass.symbol)
            .fieldBakedProperty(
                moduleClass,
                Name.identifier(nameProvider.allocate("module")),
                includedType
            )

        declarations += IncludedModuleDeclaration(
            includedType,
            false,
            PropertyPath(property),
            emptyList()
        ) { moduleExpression ->
            val constructor = includedClass.constructors.single()
            irSetField(
                moduleExpression(),
                property.backingField!!,
                irCall(constructor).apply {
                    typeArguments.forEachIndexed { index, typeArgument ->
                        putTypeArgument(index, typeArgument)
                    }

                    valueArguments
                        .filter { (valueParameter, _) ->
                            !valueParameter.type.isFunction() ||
                                    (valueParameter.type.typeArguments.firstOrNull()?.classifierOrNull != symbols.providerDsl &&
                                            !valueParameter.type.hasAnnotation(InjektFqNames.Module))
                        }
                        .map { it.second }
                        .forEachIndexed { index, valueArgument ->
                            putValueArgument(index, valueArgument())
                        }
                }
            )
        }

        includedDescriptor
            .functions
            .filter { it.descriptor.annotations.hasAnnotation(InjektFqNames.AstModule) }
            .filter { it.descriptor.annotations.hasAnnotation(InjektFqNames.AstInline) }
            .forEach { innerIncludeFunction ->
                val moduleExpression =
                    innerIncludeFunction.getAnnotation(InjektFqNames.AstValueParameterPath)!!
                        .getValueArgument(0)!!
                        .let { it as IrConst<String> }.value
                        .let { valueParameterName ->
                            val index = function
                                .allParameters
                                .indexOfFirst { it.name.asString() == valueParameterName }
                            valueArguments[index].second
                        }()

                when (moduleExpression) {
                    is IrFunctionExpression -> {
                        includeModuleFromFunction(
                            moduleExpression.function,
                            emptyList(),
                            innerIncludeFunction.valueParameters
                                .map { valueParameter ->
                                    val innerProperty =
                                        valueParameter.getAnnotation(InjektFqNames.AstPropertyPath)!!
                                            .getValueArgument(0)!!
                                            .let { it as IrConst<String> }.value
                                            .let { propertyName ->
                                                includedClass.properties
                                                    .single { it.name.asString() == propertyName }
                                            }

                                    valueParameter to {
                                        DeclarationIrBuilder(
                                            pluginContext,
                                            property.symbol
                                        ).run {
                                            irCall(innerProperty.getter!!).apply {
                                                dispatchReceiver = irCall(property.getter!!).apply {
                                                    dispatchReceiver =
                                                        irGet(module.clazz.thisReceiver!!)
                                                }
                                            }
                                        }
                                    }
                                },
                            declarations
                        )
                    }
                    is IrGetValue -> {
                        val innerProperties = innerIncludeFunction.valueParameters
                            .mapIndexed { index, valueParameter ->
                                InjektDeclarationIrBuilder(pluginContext, moduleClass.symbol)
                                    .fieldBakedProperty(
                                        moduleClass,
                                        Name.identifier("${innerIncludeFunction.name}\$p$index"),
                                        valueParameter.type
                                    )
                            }

                        declarations += IncludedModuleDeclaration(
                            innerIncludeFunction.returnType,
                            true,
                            ValueParameterPath(moduleExpression.symbol.owner as IrValueParameter),
                            innerProperties
                                .map { property ->
                                    IncludedModuleDeclaration.Parameter(
                                        PropertyPath(property),
                                        property.getter!!.returnType
                                    )
                                }
                        ) { moduleExpression ->
                            irBlock {
                                innerIncludeFunction.valueParameters.forEachIndexed { index, innerValueParameter ->
                                    val innerProperty =
                                        innerValueParameter.getAnnotation(InjektFqNames.AstPropertyPath)!!
                                            .getValueArgument(0)!!
                                            .let { it as IrConst<String> }.value
                                            .let { propertyName ->
                                                includedClass.properties
                                                    .single { it.name.asString() == propertyName }
                                            }

                                    +irSetField(
                                        moduleExpression(),
                                        innerProperties[index].backingField!!,
                                        DeclarationIrBuilder(
                                            pluginContext,
                                            innerProperty.symbol
                                        ).irCall(innerProperty.getter!!).apply {
                                            dispatchReceiver = irCall(property.getter!!).apply {
                                                dispatchReceiver =
                                                    irGet(module.clazz.thisReceiver!!)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    else -> error("Unexpected include function expression ${innerIncludeFunction.dump()}")
                }
            }

        declarations += includedDescriptor
            .functions
            .filter { it.descriptor.annotations.hasAnnotation(InjektFqNames.AstBinding) }
            .filter { it.descriptor.annotations.hasAnnotation(InjektFqNames.AstInline) }
            .map { bindingFunction ->
                val singleArgumentExpression: IrExpression?
                val bindingType: IrType
                when {
                    bindingFunction.hasAnnotation(InjektFqNames.AstTypeParameterPath) -> {
                        bindingType =
                            bindingFunction.getAnnotation(InjektFqNames.AstTypeParameterPath)!!
                                .getValueArgument(0)!!
                                .let { it as IrConst<String> }.value
                                .let { typeParameterName ->
                                    val index = includedClass.typeParameters
                                        .indexOfFirst { it.name.asString() == typeParameterName }
                                    typeArguments[index]
                                }
                                .withAnnotations(
                                    pluginContext, moduleClass.symbol,
                                    bindingFunction.returnType.toKotlinType().annotations.toList()
                                )
                        singleArgumentExpression = null
                    }
                    bindingFunction.hasAnnotation(InjektFqNames.AstValueParameterPath) -> {
                        singleArgumentExpression =
                            bindingFunction.getAnnotation(InjektFqNames.AstValueParameterPath)!!
                                .getValueArgument(0)!!
                                .let { it as IrConst<String> }.value
                                .let { valueParameterName ->
                                    val index = function
                                        .allParameters
                                        .indexOfFirst { it.name.asString() == valueParameterName }
                                    valueArguments[index].second
                                }()
                        bindingType = singleArgumentExpression.type.typeArguments.last()
                            .withAnnotations(
                                pluginContext, moduleClass.symbol,
                                bindingFunction.returnType.toKotlinType().annotations.toList()
                            )
                    }
                    else -> {
                        error("Unexpected inline binding ${bindingFunction.dump()}")
                    }
                }

                createBindingDeclarationFromSingleArgument(
                    bindingType,
                    singleArgumentExpression,
                    false,
                    bindingFunction.hasAnnotation(InjektFqNames.AstScoped)
                )
            }
    }

    private fun createBindingDeclarationFromSingleArgument(
        bindingType: IrType,
        singleArgument: IrExpression?,
        instance: Boolean,
        scoped: Boolean
    ): BindingDeclaration {
        val bindingPath: Path
        val inline: Boolean
        val parameters =
            mutableListOf<InjektDeclarationIrBuilder.FactoryParameter>()
        val statement: (IrBuilderWithScope.(() -> IrExpression) -> IrStatement)?

        fun addParametersFromProvider(provider: IrClass) {
            val assisted = provider.functions
                .single { it.name.asString() == "invoke" }
                .valueParameters
                .map { it.type }

            val nonAssisted = provider.constructors
                .single()
                .valueParameters
                .filter { it.name.asString() != "module" }
                .map { it.type.typeArguments.single() }

            parameters += (assisted + nonAssisted).mapIndexed { index, type ->
                InjektDeclarationIrBuilder.FactoryParameter(
                    name = "p$index",
                    type = type,
                    assisted = type in assisted,
                    requirement = false
                )
            }
        }

        if (singleArgument == null) {
            if (bindingType.toKotlinType().isTypeParameter()) {
                bindingPath = TypeParameterPath(
                    module.function.typeParameters.single {
                        it.descriptor ==
                                bindingType.toKotlinType().constructor.declarationDescriptor
                    }
                )
                inline = true
                statement = null
            } else {
                val provider = providerFactory.providerForClass(
                    name = Name.identifier(nameProvider.allocate("Factory")),
                    clazz = bindingType.classOrFail
                        .ensureBound(pluginContext.irProviders).owner,
                    visibility = module.clazz.visibility
                )
                module.clazz.addChild(provider)
                addParametersFromProvider(provider)
                bindingPath = ClassPath(provider)
                inline = false
                statement = null
            }
        } else {
            if (instance) {
                val property = InjektDeclarationIrBuilder(pluginContext, moduleClass.symbol)
                    .fieldBakedProperty(
                        moduleClass,
                        Name.identifier(nameProvider.allocate("instance")),
                        bindingType
                    )

                statement = {
                    irSetField(
                        it(),
                        property.backingField!!,
                        singleArgument
                    )
                }
                bindingPath = PropertyPath(property)
                inline = false
            } else {
                when (singleArgument) {
                    is IrFunctionExpression -> {
                        val provider = providerFactory.providerForDefinition(
                            name = Name.identifier(nameProvider.allocate("Factory")),
                            definition = singleArgument,
                            visibility = module.clazz.visibility,
                            moduleFieldsByParameter = module.fieldsByParameters
                        )
                        module.clazz.addChild(provider)
                        addParametersFromProvider(provider)
                        bindingPath = ClassPath(provider)
                        inline = false
                        statement = null
                    }
                    is IrGetValue -> {
                        bindingPath = ValueParameterPath(
                            module.function.valueParameters.single {
                                it.symbol == singleArgument.symbol
                            }
                        )
                        inline = true
                        statement = null
                    }
                    else -> error("Unexpected definition ${singleArgument.dump()}")
                }
            }
        }

        return BindingDeclaration(
            bindingType = bindingType,
            parameters = parameters,
            scoped = scoped,
            inline = inline,
            path = bindingPath,
            statement = statement
        )
    }

}
