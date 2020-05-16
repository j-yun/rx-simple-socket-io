package net.owlfamily.android.rxsimplesocket

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

object NetworkUtil  {
    fun getLocalIpAddress(): String? {
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val networkInterface = en.nextElement()
                val address = networkInterface.inetAddresses
                while (address.hasMoreElements()) {
                    val inetAddress = address.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.getHostAddress()
                    }
                }
            }
        } catch (ex: SocketException) {
            ex.printStackTrace()
        }
        return null
    }
}