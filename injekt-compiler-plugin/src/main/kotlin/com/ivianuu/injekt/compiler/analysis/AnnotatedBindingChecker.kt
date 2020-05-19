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

package com.ivianuu.injekt.compiler.analysis

import com.ivianuu.injekt.compiler.InjektErrors
import com.ivianuu.injekt.compiler.InjektFqNames
import com.ivianuu.injekt.compiler.getAnnotatedAnnotations
import com.ivianuu.injekt.compiler.hasAnnotatedAnnotations
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext
import org.jetbrains.kotlin.resolve.descriptorUtil.module

class AnnotatedBindingChecker : DeclarationChecker {
    override fun check(
        declaration: KtDeclaration,
        descriptor: DeclarationDescriptor,
        context: DeclarationCheckerContext
    ) {
        if (descriptor !is ClassDescriptor) return
        if (!descriptor.annotations.hasAnnotation(InjektFqNames.Transient) &&
            !descriptor.hasAnnotatedAnnotations(InjektFqNames.Scope, descriptor.module)
        ) return

        if (descriptor.annotations.hasAnnotation(InjektFqNames.Transient) &&
            descriptor.hasAnnotatedAnnotations(InjektFqNames.Scope, descriptor.module)
        ) {
            context.trace.report(
                InjektErrors.TRANSIENT_WITH_SCOPED
                    .on(declaration)
            )
        }

        if (descriptor.getAnnotatedAnnotations(InjektFqNames.Scope, descriptor.module).size > 1) {
            context.trace.report(
                InjektErrors.MULTIPLE_SCOPES
                    .on(declaration)
            )
        }

        if ((descriptor.kind != ClassKind.CLASS && descriptor.kind != ClassKind.OBJECT) || descriptor.modality == Modality.ABSTRACT) {
            context.trace.report(InjektErrors.ANNOTATED_BINDING_CANNOT_BE_ABSTRACT.on(declaration))
        }
    }
}