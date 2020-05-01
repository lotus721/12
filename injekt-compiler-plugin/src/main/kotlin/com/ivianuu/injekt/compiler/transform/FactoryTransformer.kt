package com.ivianuu.injekt.compiler.transform

import com.ivianuu.injekt.compiler.InjektFqNames
import com.ivianuu.injekt.compiler.InjektNameConventions
import com.ivianuu.injekt.compiler.graph.ComponentNode
import com.ivianuu.injekt.compiler.graph.Graph
import com.ivianuu.injekt.compiler.graph.Key
import com.ivianuu.injekt.compiler.graph.ModuleNode
import com.ivianuu.injekt.compiler.graph.child
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.addChild
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.ir.copyTypeParametersFrom
import org.jetbrains.kotlin.backend.common.ir.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addGetter
import org.jetbrains.kotlin.ir.builders.declarations.addProperty
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.copyValueArgumentsFrom
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.fields
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.fqNameForIrSerialization
import org.jetbrains.kotlin.ir.util.isFakeOverride
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.BindingTrace

class FactoryTransformer(
    context: IrPluginContext,
    bindingTrace: BindingTrace,
    private val declarationStore: InjektDeclarationStore
) : AbstractInjektTransformer(context, bindingTrace) {

    private val factoryFunctions = mutableListOf<IrFunction>()
    private val transformedFactories = mutableMapOf<IrFunction, IrClass>()
    private val transformingFactories = mutableSetOf<FqName>()
    private var computedFactoryFunctions = false

    override fun visitModuleFragment(declaration: IrModuleFragment): IrModuleFragment {
        computeFactoryFunctionsIfNeeded()

        factoryFunctions.forEach { function ->
            DeclarationIrBuilder(context, function.symbol).run {
                getImplementationClassForFactory(function)
            }
        }

        return super.visitModuleFragment(declaration)
    }

    // todo simplify implementation if we don't need it
    fun getImplementationClassForFactory(fqName: FqName): IrClass? {
        transformedFactories.values.firstOrNull {
            it.fqNameForIrSerialization == fqName
        }?.let { return it }

        val function = factoryFunctions.firstOrNull {
            val packageName = it.fqNameForIrSerialization.parent()
            packageName.child(
                InjektNameConventions.getModuleNameForModuleFunction(it.name)
            ) == fqName
        } ?: return null

        return getImplementationClassForFactory(function)
    }

    fun getImplementationClassForFactory(function: IrFunction): IrClass? {
        computeFactoryFunctionsIfNeeded()
        check(function in factoryFunctions) {
            "Unknown function $function"
        }
        transformedFactories[function]?.let { return it }
        return DeclarationIrBuilder(context, function.symbol).run {
            val packageName = function.fqNameForIrSerialization.parent()
            val implementationName =
                InjektNameConventions.getImplementationNameForFactoryFunction(function.name)
            val implementationFqName = packageName.child(implementationName)
            check(implementationFqName !in transformingFactories) {
                "Circular dependency for factory $implementationFqName"
            }
            transformingFactories += implementationFqName
            val implementationClass = implementationClass(function)
            println(implementationClass.dump())
            function.file.addChild(implementationClass)
            function.body = irExprBody(irCall(implementationClass.constructors.single()))
            transformedFactories[function] = implementationClass
            transformingFactories -= implementationFqName
            implementationClass
        }
    }

    private fun computeFactoryFunctionsIfNeeded() {
        if (computedFactoryFunctions) return
        computedFactoryFunctions = true
        moduleFragment.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitFunction(declaration: IrFunction): IrStatement {
                if (declaration.annotations.hasAnnotation(InjektFqNames.Factory)) {
                    factoryFunctions += declaration
                }

                return super.visitFunction(declaration)
            }
        })
    }

    private fun IrBuilderWithScope.implementationClass(
        function: IrFunction
    ) = buildClass {
        // todo make kind = OBJECT if this component has no state
        name = InjektNameConventions.getImplementationNameForFactoryFunction(function.name)
    }.apply clazz@{
        createImplicitParameterDeclarationWithWrappedDescriptor()

        superTypes += function.returnType

        val moduleCall = function.body?.statements?.single()
            ?.let { (it as? IrReturn)?.value }
            ?.let { (it as? IrCall)?.getValueArgument(0) as? IrFunctionExpression }
            ?.function
            ?.body
            ?.statements
            ?.single() as? IrCall

        val moduleFqName = moduleCall?.symbol?.owner?.fqNameForIrSerialization
            ?.parent()
            ?.child(InjektNameConventions.getModuleNameForModuleFunction(moduleCall.symbol.owner.name))

        val module = if (moduleFqName != null) declarationStore.getModule(moduleFqName)
        else null

        val moduleField: Lazy<IrField>? = if (module != null) lazy {
            addField("module", module.defaultType)
        } else null

        val componentNode = ComponentNode(
            component = this,
            treeElement = { it }
        )

        val graph = Graph(
            context = this@FactoryTransformer.context,
            symbols = symbols,
            thisComponent = componentNode,
            thisComponentModule = module?.let {
                ModuleNode(
                    module = module,
                    treeElement = componentNode.treeElement.child(moduleField!!.value)
                )
            },
            declarationStore = declarationStore
        )

        val dependencyRequests = mutableMapOf<IrDeclaration, Key>()

        fun IrClass.collectDependencyRequests() {
            for (declaration in declarations) {
                when (declaration) {
                    is IrFunction -> {
                        if (declaration !is IrConstructor &&
                            declaration.dispatchReceiverParameter?.type != irBuiltIns.anyType &&
                            !declaration.isFakeOverride
                        )
                            dependencyRequests[declaration] = Key(declaration.returnType)
                    }
                    is IrProperty -> {
                        if (!declaration.isFakeOverride)
                            dependencyRequests[declaration] = Key(declaration.getter!!.returnType)
                    }
                }
            }

            superTypes
                .mapNotNull { it.classOrNull?.owner }
                .forEach { it.collectDependencyRequests() }
        }

        superTypes.single().classOrNull?.owner?.collectDependencyRequests()

        dependencyRequests.forEach { declaration, key ->
            val binding = graph.requestBinding(key)
            when (declaration) {
                is IrFunction -> {
                    addFunction {
                        name = declaration.name
                        returnType = declaration.returnType
                    }.apply {
                        overriddenSymbols += declaration.symbol as IrSimpleFunctionSymbol
                        dispatchReceiverParameter = thisReceiver!!.copyTo(this)
                        body = irExprBody(
                            irCall(binding.getFunction(this@implementationClass)).apply {
                                dispatchReceiver = irGet(dispatchReceiverParameter!!)
                            }
                        )
                    }
                }
                is IrProperty -> {
                    addProperty {
                        name = declaration.name
                    }.apply {
                        addGetter {
                            returnType = declaration.getter!!.returnType
                        }.apply {
                            overriddenSymbols += declaration.getter!!.symbol as IrSimpleFunctionSymbol
                            dispatchReceiverParameter = thisReceiver!!.copyTo(this)
                            body = irExprBody(
                                irCall(binding.getFunction(this@implementationClass)).apply {
                                    dispatchReceiver = irGet(dispatchReceiverParameter!!)
                                }
                            )
                        }
                    }
                }
            }
        }

        addConstructor {
            returnType = defaultType
            isPrimary = true
        }.apply {
            copyTypeParametersFrom(this@clazz)

            body = irBlockBody {
                initializeClassWithAnySuperClass(this@clazz.symbol)

                if (moduleField?.isInitialized() == true) {
                    +irSetField(
                        irGet(thisReceiver!!),
                        moduleField.value,
                        irCall(module!!.constructors.single()).apply {
                            copyValueArgumentsFrom(
                                moduleCall!!,
                                moduleCall.symbol.owner,
                                symbol.owner
                            )
                        }
                    )
                }

                val fieldsToInitialize = graph.resolvedBindings.values
                    .filter { it.providerField() != null }
                    .filter { it.providerField() in fields }

                val initializedKeys = mutableSetOf<Key>()
                val processedFields = mutableSetOf<IrField>()

                while (true) {
                    val fieldsToProcess = fieldsToInitialize
                        .filter { it.providerField() !in processedFields }
                    if (fieldsToProcess.isEmpty()) break

                    fieldsToProcess
                        .filter {
                            it.dependencies.all {
                                it in initializedKeys
                            }
                        }
                        .forEach {
                            initializedKeys += it.key
                            processedFields += it.providerField()!!
                            +irSetField(
                                irGet(thisReceiver!!),
                                it.providerField()!!,
                                it.providerInstance(this, irGet(thisReceiver!!))
                            )
                        }
                }
            }
        }
    }

}
