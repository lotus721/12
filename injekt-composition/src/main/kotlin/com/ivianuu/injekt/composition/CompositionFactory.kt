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

package com.ivianuu.injekt.composition

import com.ivianuu.injekt.Qualifier
import com.ivianuu.injekt.internal.TypeAnnotation
import com.ivianuu.injekt.internal.injektIntrinsic

@TypeAnnotation
@Qualifier
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.EXPRESSION, AnnotationTarget.TYPE)
annotation class CompositionFactory

inline fun <reified T, reified F> compositionFactoryOf(): F =
    CompositionFactories.get(T::class)

fun generateCompositions(): Unit = injektIntrinsic()