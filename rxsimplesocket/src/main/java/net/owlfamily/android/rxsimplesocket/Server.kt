package net.owlfamily.android.rxsimplesocket

import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import net.owlfamily.android.rxsimplesocket.*
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean

class Server(private val listenPort:Int) : Observable<Event<*>>(), Disposable {
    private lateinit var serverSocket: ServerSocket

    private val unsubscribed = AtomicBoolean()
    private val disposables = CompositeDisposable()

    override fun isDisposed(): Boolean { return unsubscribed.get() }
    override fun dispose() {
        unsubscribed.compareAndSet(false, true)
        flush()
    }

    private fun flush(){
        disposables.clear()
        serverSocket.close()
    }

    override fun subscribeActual(observer: Observer<in Event<*>>) {
        observer.onSubscribe(this)
        observer.onNext(EventLog("Start Server : ${NetworkUtil.getLocalIpAddress()}:${listenPort}"))
        try {
            serverSocket = ServerSocket(listenPort)
        }catch (e:Exception){
            e.printStackTrace()
            observer.onError(e)
            return
        }

        while (
            !Thread.currentThread().isInterrupted
            && !isDisposed
            && !serverSocket.isClosed) {

            try {
                val clientSocket = serverSocket.accept()

                val communicator = Communicator(clientSocket)
                observer.onNext(EventHandshake(communicator))
            }catch (e:Exception){
                e.printStackTrace()
                observer.onNext(EventError(e))
            }
        }

        observer.onNext(EventLog("Finish Server : ${NetworkUtil.getLocalIpAddress()}:${listenPort}"))
        observer.onComplete()
        flush()
    }
}