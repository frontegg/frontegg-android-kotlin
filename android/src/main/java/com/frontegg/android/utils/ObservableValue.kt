package com.frontegg.android.utils

import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

class NullableObject<K>(var value: K)

/**
 * Read only Observable Value.
 * Observe value changes with [stateFlow] (Kotlin Flow, Compose friendly).
 * @param T is the type of [ReadOnlyObservableValue];
 * @property stateFlow emits the current value and every distinct change;
 * @property value is the read only value of [ReadOnlyObservableValue];
 */
abstract class ReadOnlyObservableValue<T>(value: T) {

    private val mutableStateFlow: MutableStateFlow<T> = MutableStateFlow(value)

    /**
     * Hot [StateFlow] of this value. Collect it from a coroutine, or in Compose with
     * `collectAsState()`. Like every [StateFlow], it skips values equal to the current one.
     */
    val stateFlow: StateFlow<T> = mutableStateFlow.asStateFlow()

    private val listeners = CopyOnWriteArrayList<(T) -> Unit>()

    @Deprecated(
        message = "RxJava support will be removed in the next major version. Collect stateFlow in a coroutine instead."
    )
    val observable: PublishSubject<NullableObject<T>> = PublishSubject.create()

    private val valueLock = Any()

    @Volatile
    private var latestValue: T = value

    /** The value set last, even when it equals the previous one and [stateFlow] keeps the earlier instance. */
    open val value: T
        get() {
            return latestValue
        }

    /**
     * Subscription method of [ReadOnlyObservableValue].
     * [onNext] runs on the setter's thread, outside any lock; order across setter threads is not guaranteed.
     * @param onNext is the consumer of every value set, starting with the current one.
     * @return Disposable object for dispose the consumer.
     */
    @Deprecated(
        message = "RxJava support will be removed in the next major version. Collect stateFlow in a coroutine instead."
    )
    @Suppress("DEPRECATION")
    fun subscribe(onNext: Consumer<NullableObject<T>>): Disposable {
        val disposable = observable.subscribe(onNext)
        onNext.accept(NullableObject(latestValue))
        return disposable
    }

    /** Calls [listener] on the setter's thread, outside any lock; order across threads is not guaranteed. */
    internal fun observe(listener: (T) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    /** Like [observe], then calls [listener] once with the current value, which a concurrent set may repeat. */
    internal fun observeFromCurrent(listener: (T) -> Unit): () -> Unit {
        val remove = observe(listener)
        runCatching { listener(latestValue) }
            .onFailure { remove() }
            .getOrThrow()
        return remove
    }

    @Suppress("DEPRECATION")
    protected fun publish(newValue: T) {
        synchronized(valueLock) {
            latestValue = newValue
            mutableStateFlow.value = newValue
        }
        listeners.forEach { it(newValue) }
        observable.onNext(NullableObject(newValue))
    }
}

class ObservableValue<T>(value: T) : ReadOnlyObservableValue<T>(value) {

    override var value: T
        get() {
            return super.value
        }
        set(newValue) {
            publish(newValue)
        }
}
