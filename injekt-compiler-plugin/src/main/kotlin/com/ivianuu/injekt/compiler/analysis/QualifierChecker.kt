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
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.KotlinRetention
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.AnnotationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext
import org.jetbrains.kotlin.resolve.descriptorUtil.getAnnotationRetention

class QualifierChecker : DeclarationChecker {
    override fun check(
        declaration: KtDeclaration,
        descriptor: DeclarationDescriptor,
        context: DeclarationCheckerContext
    ) {
        if (descriptor !is ClassDescriptor) return
        if (!descriptor.annotations.hasAnnotation(InjektFqNames.Qualifier)) return
        val retention = descriptor.getAnnotationRetention() ?: KotlinRetention.RUNTIME
        val targets = AnnotationChecker.applicableTargetSet(descriptor) ?: return

        if (retention != KotlinRetention.RUNTIME) {
            context.trace.report(InjektErrors.MUST_HAVE_RUNTIME_RETENTION.on(declaration))
        }

        if (KotlinTarget.EXPRESSION !in targets || KotlinTarget.TYPE !in targets) {
            context.trace.report(InjektErrors.MISSING_QUALIFIER_TARGETS.on(declaration))
        }

    }
}