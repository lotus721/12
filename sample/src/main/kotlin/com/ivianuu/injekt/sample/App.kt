package com.ivianuu.injekt.sample

import android.app.Application
import android.content.Context
import com.ivianuu.injekt.Assisted
import com.ivianuu.injekt.Factory
import com.ivianuu.injekt.MembersInjector
import com.ivianuu.injekt.Module
import com.ivianuu.injekt.Provider
import com.ivianuu.injekt.Qualifier
import com.ivianuu.injekt.Transient
import com.ivianuu.injekt.childFactory
import com.ivianuu.injekt.createImplementation
import com.ivianuu.injekt.internal.InjektAst
import com.ivianuu.injekt.internal.InstanceProvider
import com.ivianuu.injekt.internal.injektIntrinsic
import com.ivianuu.injekt.map
import com.ivianuu.injekt.transient

class App : Application() {
    val component by lazy { createAppComponent(this) }
}

interface AppComponent {
    val repo: Repo
    val activityComponentFactory: (MainActivity) -> ActivityComponent
}

@Factory
fun createAppComponent(app: App): AppComponent = createImplementation {
    childFactory(::activityComponentFactory)
}

private class createAppComponentModuleImpl {
    @InjektAst.Module
    interface Descriptor {
        @InjektAst.ChildFactory
        fun childFactory_0(): activityComponentFactoryModule
    }
}

private class createAppComponentImpl : AppComponent {
    private val repoProvider: Provider<Repo> = injektIntrinsic()
    override val repo: Repo get() = repoProvider()

    override val activityComponentFactory: (MainActivity) -> ActivityComponent
        get() = activityComponentFactoryImpl(this)

    private class activityComponentFactoryImpl(
        private val appComponentImpl: createAppComponentImpl
    ) : (MainActivity) -> ActivityComponent {
        override fun invoke(p1: MainActivity): ActivityComponent {
            return activityComponentImpl(appComponentImpl)
        }
    }

    private class activityComponentImpl(
        private val appComponentImpl: createAppComponentImpl
    ) : ActivityComponent {
        override val injector: MembersInjector<MainActivity>
            get() = injectorProvider()
        private val viewModelProvider: Provider<HomeViewModel> = injektIntrinsic()
        private val injectorProvider: Provider<MembersInjector<MainActivity>> = InstanceProvider(
            MainActivity.MainActivityMembersInjector(viewModelProvider)
        )
    }
}

@Transient
class MyWorker(
    @Assisted private val context: Context,
    @Assisted private val workerParameters: WorkerParameters
) : Worker(context, workerParameters)

class InjektWorkerFactory(
    @Workers private val workers: Map<String, SingleWorkerFactory>
) : WorkerFactory() {
    override fun create(
        className: String,
        context: Context,
        workerParameters: WorkerParameters
    ): Worker {
        return workers.getValue(className)(context, workerParameters)
    }
}

@Target(
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.EXPRESSION,
    AnnotationTarget.FUNCTION
)
@Retention(AnnotationRetention.SOURCE)
@Qualifier
private annotation class Workers

typealias SingleWorkerFactory = (Context, WorkerParameters) -> Worker

@Module
fun applicationModule() {
    @ForApplication
    transient { (context: Context, workerParameters: WorkerParameters) ->
        MyWorker(context, workerParameters)
    }
    @Workers map<String, SingleWorkerFactory> {
        put<(Context, WorkerParameters) -> MyWorker>(MyWorker::class.java.name)
    }
}
