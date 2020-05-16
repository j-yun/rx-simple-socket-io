package net.owlfamily.android.rxsimplesocket

import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.BehaviorSubject
import java.util.concurrent.atomic.AtomicBoolean


@Suppress("MemberVisibilityCanBePrivate")
abstract class Channel<T>(val isHost:Boolean, val key:String, val disposables:CompositeDisposable) {
    val log:AtomicBoolean = AtomicBoolean(false)

    enum class Status {
        None, Open, Close
    }

    val dataReceiver:BehaviorSubject<T> = BehaviorSubject.create()
    val dataSender:BehaviorSubject<T> = BehaviorSubject.create()
    val status:BehaviorSubject<Status> = BehaviorSubject.createDefault(Status.None)

    fun close(){
        status.onNext(Status.Close)
    }

    fun clear(){
        status.onNext(Status.Close)
        disposables.clear()
        dataReceiver.onComplete()
        dataSender.onComplete()
        status.onComplete()
    }
}


class StringChannel(isHost:Boolean, key:String, disposables:CompositeDisposable): Channel<String>(isHost, key, disposables)