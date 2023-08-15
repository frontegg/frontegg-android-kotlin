package com.frontegg.android.utils

import android.os.Handler
import android.os.Looper
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.subjects.PublishSubject

class NullableObject<K>(var value: K)

class ObservableValue<T>(value: T) {

    val observable: PublishSubject<NullableObject<T>> = PublishSubject.create()

    private var nullableObject: NullableObject<T>
    var value: T
        set(newValue) {
            nullableObject.value = newValue
            observable.onNext(nullableObject)
        }
        get() {
            return nullableObject.value
        }

    init {
        this.nullableObject = NullableObject(value)
    }

    fun subscribe(onNext: Consumer<NullableObject<T>>): Disposable {
        observable.subscribe()
        val disposable = observable.subscribe(onNext)
        onNext.accept(nullableObject)
        return disposable
    }
}