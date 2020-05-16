package net.owlfamily.android.rxsimplesocket

import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class Client(private val serverAddress:String, private val port:Int) : Observable<Event<*>>(), Disposable {
    private lateinit var socket: Socket

    private var communicator: Communicator? = null

    private val unsubscribed = AtomicBoolean()
    private val disposables = CompositeDisposable()

    override fun isDisposed(): Boolean { return unsubscribed.get() }

    override fun dispose() {
        unsubscribed.compareAndSet(false, true)
        communicator?.dispose()
        disposables.clear()
    }

    override fun subscribeActual(observer: Observer<in Event<*>>) {
        observer.onNext(EventLog("Start Client : ${NetworkUtil.getLocalIpAddress()} -> connect to $serverAddress:$port"))
        try {
            socket = Socket(serverAddress, port)
        }catch (e:Exception){
            e.printStackTrace()
            observer.onError(e)
            return
        }

        if(socket.isBound){
            observer.onNext(EventLog("Connected to server"))
        }

        communicator = Communicator(socket)
        observer.onNext(EventHandshake(communicator!!))
    }

    fun close(){
        communicator?.close()
    }
}