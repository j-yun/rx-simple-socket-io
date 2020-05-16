package net.owlfamily.android.rxsimplesocket.demo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import net.owlfamily.android.rxsimplesocket.*

class ServerActivity : AppCompatActivity() {

    companion object {
        const val port = 7879
        const val ServerTag = "Server"
    }

    private var serverDisposables: CompositeDisposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server)
        startServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
    }

    private fun appendLog(tag:String, text:String){
        Log.d(tag,text)
    }

    private fun stopServer(){
        serverDisposables?.dispose()
    }

    private fun startServer(){
        stopServer()
        serverDisposables = CompositeDisposable()

        val server = Server(port)
        server.subscribeOn(Schedulers.newThread()).doOnDispose {
            appendLog(ServerTag,"Server Disposed")
        }.doOnComplete {
            appendLog(ServerTag,"Server Complete")
        }.observeOn(AndroidSchedulers.mainThread()).subscribe({ event ->
            appendLog(ServerTag, "$event")

            if(event is EventHandshake){
                val communicator = event.data
                communicator.subscribeOn(Schedulers.io()).doOnDispose {
                    appendLog(ServerTag, "communicator disposed")
                }.doOnComplete {
                    appendLog(ServerTag, "communicator complete")
                }.observeOn(AndroidSchedulers.mainThread()).subscribe ({ communicatorEvent ->
                    appendLog(ServerTag, "$communicatorEvent")

                    if(communicatorEvent is EventChannelCreated){
                        val channel = communicatorEvent.data
                        channel.dataReceiver.subscribe {
                            channel.dataSender.onNext("Echo : $it")
                        }.addTo(serverDisposables!!)
                    }
                },{error ->
                    appendLog(ServerTag, "${error.message}")
                }).addTo(serverDisposables!!)
            }
        },{ error ->
            appendLog("Server", "${error.message}")
            error.printStackTrace()
        }).addTo(serverDisposables!!)
    }
}
