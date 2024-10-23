package com.frontegg.android.utils

import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.subjects.PublishSubject

class NullableObject<K>(var value: K)

abstract class ReadOnlyObservableValue<T>(value: T) {

    val observable: PublishSubject<NullableObject<T>> = PublishSubject.create()

    protected var nullableObject: NullableObject<T> = NullableObject(value)
    open val value: T
        get() {
            return nullableObject.value
        }

    fun subscribe(onNext: Consumer<NullableObject<T>>): Disposable {
        observable.subscribe()
        val disposable = observable.subscribe(onNext)
        onNext.accept(nullableObject)
        return disposable
    }
}

class ObservableValue<T>(value: T) : ReadOnlyObservableValue<T>(value) {

    override var value: T
        get() {
            return nullableObject.value
        }
        set(newValue) {
            nullableObject.value = newValue
            observable.onNext(nullableObject)
        }
}