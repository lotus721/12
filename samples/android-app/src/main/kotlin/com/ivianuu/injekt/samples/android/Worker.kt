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

package com.ivianuu.injekt.samples.android

import android.content.Context
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ivianuu.injekt.Reader
import com.ivianuu.injekt.android.AndroidApplicationContext
import com.ivianuu.injekt.android.work.GivenWorker
import com.ivianuu.injekt.given

@GivenWorker
class TestWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    init {
        println("hello $context $workerParams ${given<Repo>()}")
    }

    override suspend fun doWork(): Result = Result.success()

}

@Reader
fun initializeWorkers() {
    WorkManager.initialize(
        given<AndroidApplicationContext>(), Configuration.Builder()
            .setWorkerFactory(given()).build()
    )
}