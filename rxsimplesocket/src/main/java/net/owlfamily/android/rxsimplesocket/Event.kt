package net.owlfamily.android.rxsimplesocket

open class Event<T>(open val type: Type, open val data:T){
    enum class Type {
        Log,
        Error,
        Process
    }
}
data class EventError(override val data:Throwable): Event<Throwable>(type = Type.Error, data = data)
data class EventReceived(override val data:String): Event<String>(type = Type.Log, data = data)
data class EventSend(override val data:String): Event<String>(type = Type.Log, data = data)
data class EventLog(override val data:String): Event<String>(type = Type.Log, data = data)
data class EventHandshake(override val data: Communicator): Event<Communicator>(type = Type.Process, data = data)
data class EventDisconnected(override val data: Communicator): Event<Communicator>(type = Type.Process, data = data)
data class EventChannelCreated(override val data: StringChannel): Event<StringChannel>(type = Type.Process, data = data) {
    override fun toString(): String { return "EventChannelCreated : ${data.key}" }
}
data class EventChannelRemoved(override val data: StringChannel): Event<StringChannel>(type = Type.Process, data = data){
    override fun toString(): String { return "EventChannelRemoved : ${data.key}" }
}