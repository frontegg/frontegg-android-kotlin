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

    protected val mutableStateFlow: MutableStateFlow<T> = MutableStateFlow(value)

    /**
     * Hot [StateFlow] of this value. Collect it from a coroutine, or in Compose with
     * `collectAsState()`. Like every [StateFlow], it skips values equal to the current one.
     */
    val stateFlow: StateFlow<T> = mutableStateFlow.asStateFlow()

    private val listeners = CopyOnWriteArrayList<(T) -> Unit>()

    @Deprecated(
        message = "RxJava support will be removed in the next major version. Use stateFlow instead.",
        replaceWith = ReplaceWith("stateFlow")
    )
    val observable: PublishSubject<NullableObject<T>> = PublishSubject.create()

    protected var nullableObject: NullableObject<T> = NullableObject(value)
    protected val setLock = Any()
    open val value: T
        get() {
            return mutableStateFlow.value
        }

    /**
     * Subscription method of [ReadOnlyObservableValue].
     * @param onNext is the consumer of every value set, starting with the current one.
     * @return Disposable object for dispose the consumer.
     */
    @Deprecated(
        message = "RxJava support will be removed in the next major version. Use stateFlow instead.",
        replaceWith = ReplaceWith("stateFlow.collect { onNext(it) }")
    )
    @Suppress("DEPRECATION")
    fun subscribe(onNext: Consumer<NullableObject<T>>): Disposable {
        val disposable = observable.subscribe(onNext)
        onNext.accept(nullableObject)
        return disposable
    }

    /**
     * Invokes [listener] synchronously, on the setter's thread, for every value set.
     * @return a function that removes the listener.
     */
    internal fun observe(listener: (T) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    @Suppress("DEPRECATION")
    protected fun publish(newValue: T) {
        mutableStateFlow.value = newValue
        listeners.forEach { it(newValue) }
        observable.onNext(nullableObject)
    }
}

class ObservableValue<T>(value: T) : ReadOnlyObservableValue<T>(value) {

    override var value: T
        get() {
            return mutableStateFlow.value
        }
        set(newValue) {
            synchronized(setLock) {
                nullableObject.value = newValue
                publish(newValue)
            }
        }
}
