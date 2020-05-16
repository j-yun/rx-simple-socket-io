package net.owlfamily.android.rxsimplesocket


object DataType {
    const val Open = 0
    const val Close = 1
    const val Data = 2
}

data class Data(val type:Int, val key:String, val data:String)