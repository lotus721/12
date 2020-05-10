package com.ivianuu.injekt.compiler.transform.factory

import com.ivianuu.injekt.compiler.InjektSymbols
import com.ivianuu.injekt.compiler.buildClass
import com.ivianuu.injekt.compiler.classOrFail
import com.ivianuu.injekt.compiler.transform.InjektDeclarationStore
import com.ivianuu.injekt.compiler.transform.getNearestDeclarationContainer
import com.ivianuu.injekt.compiler.typeArguments
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.addChild
import org.jetbrains.kotlin.backend.common.ir.copyTypeParametersFrom
import org.jetbrains.kotlin.backend.common.ir.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.MetadataSource
import org.jetbrains.kotlin.ir.declarations.impl.IrClassImpl
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.copyTypeArgumentsFrom
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.isFakeOverride
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.util.substitute
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

class FactoryImplementation(
    val name: Name,
    val superType: IrType,
    val factoryFunction: IrFunction?,
    val parent: FactoryImplementation?,
    irDeclarationParent: IrDeclarationParent,
    moduleClass: IrClass,
    pluginContext: IrPluginContext,
    symbols: InjektSymbols,
    declarationStore: InjektDeclarationStore
) : AbstractFactoryProduct(
    moduleClass,
    pluginContext,
    symbols,
    declarationStore
) {

    val clazz = buildClass {
        this.name = this@FactoryImplementation.name
        visibility = Visibilities.PRIVATE
    }.apply {
        this.parent = irDeclarationParent
        createImplicitParameterDeclarationWithWrappedDescriptor()
        (this as IrClassImpl).metadata = MetadataSource.Class(descriptor)
        superTypes += superType
    }

    val constructor = clazz.addConstructor {
        returnType = clazz.defaultType
        isPrimary = true
        visibility = Visibilities.PUBLIC
    }.apply {
        copyTypeParametersFrom(clazz)
    }
    val factoryImplementationNode =
        FactoryImplementationNode(
            key = clazz.defaultType.asKey(pluginContext),
            factoryImplementation = this,
            initializerAccessor = { it() }
        )

    override val factoryMembers = ClassFactoryMembers(
        pluginContext,
        clazz,
        factoryImplementationNode.factoryImplementation
    )

    val parentField by lazy {
        if (parent != null) {
            clazz.addField(
                "parent",
                parent.clazz.defaultType
            )
        } else null
    }
    val parentConstructorValueParameter by lazy {
        if (parent != null) {
            constructor.addValueParameter(
                "parent",
                parent.clazz.defaultType
            )
        } else null
    }

    val moduleConstructorValueParameter = lazy {
        constructor.addValueParameter(
            "module",
            moduleClass.defaultType
        )
    }

    val dependencyRequests =
        mutableMapOf<IrDeclaration, BindingRequest>()
    val implementedRequests = mutableMapOf<IrDeclaration, IrDeclaration>()

    init {
        collectDependencyRequests()
        init(parent, dependencyRequests.values.toList()) {
            irGet(moduleConstructorValueParameter.value)
        }

        DeclarationIrBuilder(pluginContext, clazz.symbol).run {
            implementDependencyRequests()
            writeConstructor()
        }

        if (factoryFunction != null) {
            val moduleCall = factoryFunction.body!!.statements[0] as IrCall
            factoryFunction.getNearestDeclarationContainer().addChild(clazz)
            factoryFunction.body = DeclarationIrBuilder(pluginContext, factoryFunction.symbol).run {
                irExprBody(
                    irCall(constructor).apply {
                        if (constructor.valueParameters.isNotEmpty()) {
                            val moduleConstructor = moduleClass.constructors.single()
                            putValueArgument(
                                0,
                                irCall(moduleConstructor).apply {
                                    copyTypeArgumentsFrom(moduleCall)
                                    var argIndex = 0
                                    if (moduleCall.dispatchReceiver != null) {
                                        putValueArgument(argIndex++, moduleCall.dispatchReceiver)
                                    }
                                    if (moduleCall.extensionReceiver != null) {
                                        putValueArgument(argIndex++, moduleCall.extensionReceiver)
                                    }
                                    (0 until moduleCall.valueArgumentsCount).forEach {
                                        putValueArgument(
                                            argIndex++,
                                            moduleCall.getValueArgument(it)
                                        )
                                    }
                                }
                            )
                        }
                    }
                )
            }

            println(clazz.dump())
        }
    }

    private fun IrBuilderWithScope.implementDependencyRequests(): Unit = clazz.run clazz@{
        dependencyRequests.forEach { factoryExpressions.getBindingExpression(it.value) }

        dependencyRequests
            .filter { it.key !in implementedRequests }
            .forEach { (declaration, request) ->
                val binding = graph.getBinding(request)
                implementedRequests[declaration] =
                    factoryMembers.addDependencyRequestImplementation(declaration) { function ->
                        val bindingExpression = factoryExpressions.getBindingExpression(
                            BindingRequest(
                                binding.key,
                                request.requestOrigin,
                                RequestType.Instance
                            )
                        )

                        bindingExpression
                            .invoke(
                                this@implementDependencyRequests,
                                FactoryExpressionContext(this@FactoryImplementation) {
                                    irGet(function.dispatchReceiverParameter!!)
                                }
                            )
                    }
        }
    }

    private fun collectDependencyRequests() {
        fun IrClass.collectDependencyRequests(sub: IrClass?) {
            for (declaration in declarations) {
                fun reqisterRequest(type: IrType) {
                    dependencyRequests[declaration] = BindingRequest(
                        type
                            .substitute(
                                typeParameters,
                                sub?.superTypes
                                    ?.single { it.classOrNull?.owner == this }
                                    ?.typeArguments ?: emptyList()
                            )
                            .asKey(pluginContext),
                        declaration.descriptor.fqNameSafe
                    )
                }

                when (declaration) {
                    is IrFunction -> {
                        if (declaration !is IrConstructor &&
                            declaration.dispatchReceiverParameter?.type != pluginContext.irBuiltIns.anyType &&
                            !declaration.isFakeOverride
                        ) reqisterRequest(declaration.returnType)
                    }
                    is IrProperty -> {
                        if (!declaration.isFakeOverride)
                            reqisterRequest(declaration.getter!!.returnType)
                    }
                }
            }

            superTypes
                .mapNotNull { it.classOrNull?.owner }
                .forEach { it.collectDependencyRequests(this) }
        }

        val superType = clazz.superTypes.single().classOrFail.owner
        superType.collectDependencyRequests(clazz)
    }

    private fun IrBuilderWithScope.writeConstructor() = constructor.apply {
        val superType = clazz.superTypes.single().classOrFail.owner
        body = irBlockBody {
            +IrDelegatingConstructorCallImpl(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                context.irBuiltIns.unitType,
                if (superType.kind == ClassKind.CLASS)
                    superType.constructors.single { it.valueParameters.isEmpty() }
                        .symbol
                else context.irBuiltIns.anyClass.constructors.single()
            )
            +IrInstanceInitializerCallImpl(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                clazz.symbol,
                context.irBuiltIns.unitType
            )

            if (this@FactoryImplementation.parentField != null) {
                +irSetField(
                    irGet(clazz.thisReceiver!!),
                    this@FactoryImplementation.parentField!!,
                    irGet(parentConstructorValueParameter!!)
                )
            }

            factoryMembers.fields.values.forEach { (field, initializer) ->
                +irSetField(
                    irGet(clazz.thisReceiver!!),
                    field,
                    initializer(this, FactoryExpressionContext(this@FactoryImplementation) {
                        irGet(clazz.thisReceiver!!)
                    })
                )
            }
        }
    }
}
