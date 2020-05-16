package net.owlfamily.android.rxsimplesocket

import com.google.gson.Gson
import io.reactivex.*
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import java.io.*
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

@Suppress("MemberVisibilityCanBePrivate")
open class Communicator(private val socket: Socket) : Observable<Event<*>>(), Disposable {

    private val logStream = AtomicBoolean(false)

    private val channelScheduler:Scheduler = Schedulers.newThread()
    private val sendScheduler:Scheduler = Schedulers.newThread()

    private var input: BufferedReader? = null
    private var observer: Observer<in Event<*>>? = null
        @Synchronized get
        @Synchronized set

    private val unsubscribed = AtomicBoolean()
    private val disposables = CompositeDisposable()
        @Synchronized get

    private val disposablesForChannel:HashMap<String, CompositeDisposable> = HashMap()
        @Synchronized get

    private val channels:HashMap<String, StringChannel> = HashMap()
        @Synchronized get

    private val openingChannels:HashMap<String, StringChannel> = HashMap()
        @Synchronized get

    private val writeLock = ReentrantLock()


    override fun subscribeActual(observer: Observer<in Event<*>>) {
        this.observer = observer
        observer.onSubscribe(this)
        observer.onNext(EventLog("Client accepted : ${socket.inetAddress}"))

        try {
            input = BufferedReader(InputStreamReader(socket.getInputStream()))
        } catch (e: IOException) {
            e.printStackTrace()
            observer.onError(e)
            return
        }

        while (
            !Thread.currentThread().isInterrupted
            && !isDisposed
            && !socket.isClosed) {
            //checks to see if the client is still connected and displays disconnected if disconnected

            var data:String? = null
            try {
                data = input?.readLine()
            }catch (e:Throwable){
                e.printStackTrace()
            }

            if(data != null){
                var dataObject: Data? = null
                try {
                    dataObject = Gson().fromJson(data, Data::class.java)
                }catch (e: java.lang.Exception){
                    e.printStackTrace()
                }

                dataObject?.let { dataObj ->
                    val key = dataObj.key

                    when (dataObj.type) {
                        DataType.Open -> {
                            getChannelFromReceive(key).subscribe({ channel ->
                                observer.onNext(EventChannelCreated(channel))
                            },{ error ->
                                observer.onNext(EventError(error))
                            }).addTo(getDisposablesFor(key))
                        }
                        DataType.Close -> {
                            val removeDisposables = getDisposablesFor(key)

                            internalGetChannel(key)?.let { channel ->
                                removeChannel(key).subscribe({},{ error ->
                                    observer.onNext(EventError(error))
                                }).addTo(removeDisposables)
                            }
                        }
                        else -> {
                            getChannelFromReceive(key).subscribe({ channel ->
                                if(channel.log.get()){
                                    observer.onNext(EventReceived("${socket.inetAddress}, $data"))
                                }
                                channel.dataReceiver.onNext(dataObj.data)
                            },{ error ->
                                observer.onNext(EventError(error))
                            }).addTo(getDisposablesFor(key))
                        }
                    }
                }
            }
            // may socket is closed
            else {
                flush()
                observer.onComplete()
            }
        }

        observer.onNext(EventLog("Closing connection : ${socket.inetAddress}"))
        flush()
        observer.onComplete()
    }

    private fun internalRemoveChannel(key:String){
        writeLock.lock()
        val channel = channels.remove(key)
        channel?.close()
        channel?.clear()
        writeLock.unlock()

        removeDisposablesFor(key)
        if(!isDisposed && channel != null) {
            observer?.onNext(EventChannelRemoved(channel))
        }
    }

    fun removeChannel(key:String):Completable {
        return Completable.create { emitter ->
            internalGetChannel(key)?.let { channel ->
                if(channel.isHost) {
                    sendData(Data(DataType.Close, key, ""))?.subscribe({
                        internalRemoveChannel(key)
                    },{ error ->
                        observer?.onNext(EventError(error))
                    })?.addTo(getDisposablesFor(key))
                }else{
                    internalRemoveChannel(key)
                }
            }

            emitter.onComplete()
        }.subscribeOn(channelScheduler)
    }

    private fun internalHasChannel(key:String):Boolean{
        if(isDisposed) return false
        writeLock.lock()
        val result = channels.containsKey(key)
        writeLock.unlock()
        return result
    }

    fun hasChannel(key: String): Single<Boolean> {
        return Single.create<Boolean> {emitter ->
            emitter.onSuccess(internalHasChannel(key))
        }.subscribeOn(channelScheduler)
    }

    fun getDisposablesFor(key:String): CompositeDisposable {
        var result:CompositeDisposable? = disposablesForChannel[key]
        if(result == null){
            result = CompositeDisposable()
            disposablesForChannel[key] = result
        }
        return result!!
    }

    fun removeDisposablesFor(key:String){
        disposablesForChannel.remove(key)?.clear()
    }

    fun openChannel(key:String):Single<StringChannel> {
        return Single.create<StringChannel> { emitter ->
            if(internalHasChannel(key)){
                emitter.onSuccess(internalGetChannel(key)!!)
            }else{
                writeLock.lock()
                val contains = openingChannels.containsKey(key)
                writeLock.unlock()
                if(contains){
                    emitter.onError(IllegalStateException("Opening already"))
                }else{
                    val channelInstance = createChannelInstance(true, key)
                    writeLock.lock()
                    openingChannels[key] = channelInstance
                    writeLock.unlock()

                    channelInstance.status.observeOn(channelScheduler).subscribe {
                        if(it == Channel.Status.Open){
                            emitter.onSuccess(channelInstance)
                        }else if(it == Channel.Status.Close){
                            removeChannel(key).subscribe {}.addTo(channelInstance.disposables)
                        }
                    }.addTo(channelInstance.disposables)

                    // channel 열기
                    sendData(Data(DataType.Open, key, ""))?.subscribe({

                    },{ error ->
                        emitter.onError(error)
                    })?.addTo(channelInstance.disposables)
                }
            }
        }.subscribeOn(channelScheduler)
    }

    private fun getChannelFromReceive(key:String):Single<StringChannel> {
        return Single.create<StringChannel> { emitter ->
            if(internalHasChannel(key)){
                emitter.onSuccess(internalGetChannel(key)!!)
            }else{
                writeLock.lock()
                val contains = openingChannels.containsKey(key)
                writeLock.unlock()

                // 요청했던 경우
                if(contains){
                    writeLock.lock()
                    val openingChannel = openingChannels.remove(key)!!
                    channels[key] = openingChannel
                    writeLock.unlock()
                    openingChannel.status.onNext(Channel.Status.Open)
                    emitter.onSuccess(openingChannel)
                }
                // 요청당한 경우
                else{
                    val channelInstance = createChannelInstance(false, key)
                    channelInstance.status.onNext(Channel.Status.Open)

                    writeLock.lock()
                    channels[key] = channelInstance
                    writeLock.unlock()
                    channelInstance.status.observeOn(channelScheduler).subscribe {
                        if(it == Channel.Status.Open){
                            emitter.onSuccess(channelInstance)
                        }else if(it == Channel.Status.Close){
                            removeChannel(key).subscribe {}.addTo(channelInstance.disposables)
                        }
                    }.addTo(getDisposablesFor(key))

                    // subject 열기 성공 알림
                    sendData(Data(DataType.Open, key, ""))?.subscribe({

                    },{ error ->
                        emitter.onError(error)
                    })?.addTo(getDisposablesFor(key))
                }
            }
        }.subscribeOn(channelScheduler)
    }

    private fun internalGetChannel(key: String): StringChannel? {
        writeLock.lock()
        val result = channels[key]
        writeLock.unlock()
        return result
    }

    private fun createChannelInstance(isHost:Boolean, key:String): StringChannel {
        val channel = StringChannel(isHost, key,getDisposablesFor(key))

        channel.dataSender.observeOn(channelScheduler).subscribe { dataToSend ->

            sendData(Data(DataType.Data, key, dataToSend), channel.log.get())?.observeOn(channelScheduler)?.subscribe({

            },{ error ->
                observer?.onNext(EventLog("Error on send : ${socket.inetAddress}, ${error.message}"))
            })?.addTo(getDisposablesFor(key))
        }.addTo(getDisposablesFor(key))

        return channel
    }

    fun close(){
        flush()
    }

    private fun flush(){
        observer?.onNext(EventDisconnected(this))
        socket.close()

        writeLock.lock()
        for(entry in channels){
            entry.value.close()
            entry.value.clear()
            removeDisposablesFor(entry.key)
        }
        channels.clear()
        writeLock.unlock()

        disposables.clear()
    }

    private fun sendData(data: Data, log:Boolean = false):Completable?{
        if(isDisposed) { return null }
        return sendString(Gson().toJson(data),log)
    }


    private fun sendString(data:String, log:Boolean = false):Completable?{
        if(isDisposed) return null

        return Completable.create { emitter ->
            if(log){
                observer?.onNext(EventSend("${socket.inetAddress}, $data"))
            }

            try {
                val outStreamWriter = OutputStreamWriter(socket.getOutputStream())
                val bufferedWriter = BufferedWriter(outStreamWriter)
                val out = PrintWriter(bufferedWriter, false)
                out.println(data)
                out.flush()
                emitter.onComplete()
            } catch (e: Exception) {
                e.printStackTrace()
                emitter.onError(e)
            }
        }.subscribeOn(sendScheduler)
    }

    override fun isDisposed(): Boolean { return unsubscribed.get() }
    override fun dispose() {
        flush()
        unsubscribed.compareAndSet(false, true)
    }

}