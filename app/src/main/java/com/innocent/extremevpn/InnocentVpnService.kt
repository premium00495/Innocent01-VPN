package com.innocent.extremevpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

class InnocentVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        val serverIp = intent?.getStringExtra("SERVER_IP") ?: ""
        val sniHost = intent?.getStringExtra("SNI_HOST") ?: ""
        val port = intent?.getIntExtra("PORT", 443) ?: 443

        startTunnel(serverIp, port, sniHost)
        return START_STICKY
    }

    private fun startTunnel(serverIp: String, port: Int, sniHost: String) {
        if (isRunning) return

        try {
            val builder = Builder()
                .setSession("InnocentExtremeTunnel")
                .addAddress("10.0.0.2", 24)
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)

            vpnInterface = builder.establish()
            isRunning = true
        } catch (e: Exception) {
            e.printStackTrace()
            stopVpn()
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null
            isRunning = false
            stopSelf()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
