package com.ivianuu.injekt.compiler

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

object InjektFqNames {
    val InjektPackage = FqName("com.ivianuu.injekt")
    val InternalPackage = InjektPackage.child("internal")

    val InjektAst = InternalPackage.child("InjektAst")
    val AstAssisted = InjektPackage.child("Assisted")
    val AstChildFactory = InjektAst.child("ChildFactory")
    val AstDependency = InjektAst.child("Dependency")
    val AstMap = InjektAst.child("Map")
    val AstMapEntry = AstMap.child("Entry")
    val AstMapClassKey = AstMap.child("ClassKey")
    val AstMapIntKey = AstMap.child("IntKey")
    val AstMapLongKey = AstMap.child("LongKey")
    val AstMapStringKey = AstMap.child("StringKey")
    val AstModule = InjektAst.child("Module")
    val AstScope = InjektAst.child("Scope")
    val AstSet = InjektAst.child("Set")
    val AstSetElement = AstSet.child("Element")

    val Assisted = InjektPackage.child("Assisted")

    val ChildFactory = InjektPackage.child("ChildFactory")

    val Factory = InjektPackage.child("Factory")

    val MapDsl = InjektPackage.child("MapDsl")

    val Module = InjektPackage.child("Module")

    val Lazy = InjektPackage.child("Lazy")
    val MembersInjector = InjektPackage.child("MembersInjector")
    val Provider = InjektPackage.child("Provider")
    val ProviderDefinition = InjektPackage.child("ProviderDefinition")
    val ProviderDsl = InjektPackage.child("ProviderDsl")

    val Qualifier = InjektPackage.child("Qualifier")
    val Scope = InjektPackage.child("Scope")

    val SetDsl = InjektPackage.child("SetDsl")

    private fun FqName.child(name: String) = child(Name.identifier(name))

}
