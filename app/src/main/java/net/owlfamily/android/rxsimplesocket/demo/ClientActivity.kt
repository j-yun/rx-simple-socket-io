package net.owlfamily.android.rxsimplesocket.demo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import net.owlfamily.android.rxsimplesocket.*
import java.util.*
import java.util.concurrent.TimeUnit

class ClientActivity : AppCompatActivity() {

    companion object {
        const val address = "127.0.0.1"
        const val port = 7879
        const val ClientTag = "Client"
    }

    private var clientDisposables: CompositeDisposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client)
        startClient()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopClient()
    }

    private fun appendLog(tag:String, text:String){
        Log.d(tag,text)
    }

    fun stopClient(){
        clientDisposables?.dispose()
    }

    fun startClient(){
        stopClient()
        clientDisposables = CompositeDisposable()

        val client = Client(address, port)
        client.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe({ event ->
            Log.d(ClientTag,"$event")

            if(event is EventHandshake){
                val comm = event.data
                comm.subscribeOn(Schedulers.io()).doOnComplete {
                    Log.d(ClientTag,"Comm complete")
                }.doOnDispose {
                    Log.d(ClientTag,"Comm disposed")
                }.subscribe({
                    Log.d(ClientTag,"$it")
                },{
                    Log.d(ClientTag,"$it")
                }).addTo(clientDisposables!!)

                comm.openChannel("echo channel").subscribe({ channel ->
                    Observable.interval(500, TimeUnit.MILLISECONDS).take(1).subscribe {
                        channel.dataSender.onNext("Hello : ${UUID.randomUUID()}")
                    }.addTo(clientDisposables!!)

                    channel.dataReceiver.subscribe {
                        Log.d(ClientTag,"Received Message : $it")
                    }.addTo(clientDisposables!!)
                },{
                    it.printStackTrace()
                }).addTo(clientDisposables!!)
            }
        },{
            it.printStackTrace()
            Log.d(ClientTag,"$it")
        }).addTo(clientDisposables!!)
    }
}
