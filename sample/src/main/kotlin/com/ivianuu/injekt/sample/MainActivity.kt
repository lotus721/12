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

package com.ivianuu.injekt.sample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import com.ivianuu.injekt.android.ActivityViewModel
import com.ivianuu.injekt.android.activityComponent
import com.ivianuu.injekt.composition.inject
import com.ivianuu.injekt.inject

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        activityComponent.inject(this)
        super.onCreate(savedInstanceState)
        println("Got view model $viewModel")
    }
}

@ActivityViewModel
class MainViewModel(private val repo: Repo) : ViewModel() {
    init {
        println("init ")
    }

    override fun onCleared() {
        println("on cleared")
        super.onCleared()
    }
}
