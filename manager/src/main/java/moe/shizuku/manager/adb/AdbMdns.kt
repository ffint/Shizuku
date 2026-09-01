package moe.shizuku.manager.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.Observer
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket

@RequiresApi(Build.VERSION_CODES.R)
class AdbMdns(
    context: Context, private val serviceType: String,
    private val observer: Observer<Int>
) {

    @Volatile
    private var registered = false

    @Volatile
    private var running = false

    @Volatile
    private var serviceName: String? = null

    @Volatile
    var resolvedHost: String? = null
        private set

    @Volatile
    private var listener: DiscoveryListener? = null
    private var pendingRestart = false
    private var restartScheduled = false
    private var restartAttempts = 0

    private val nsdManager: NsdManager = context.getSystemService(NsdManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val restartRunnable = Runnable {
        restartScheduled = false
        if (running && listener == null) {
            startDiscovery()
        }
    }

    private val discoveryTimeoutRunnable = Runnable {
        if (running && serviceName == null) {
            scheduleRestart("discovery timeout")
        }
    }

    fun start() {
        if (running) return
        running = true
        serviceName = null
        resolvedHost = null
        restartAttempts = 0
        startDiscovery()
    }

    fun stop() {
        if (!running && listener == null) return
        running = false
        serviceName = null
        resolvedHost = null
        pendingRestart = false
        restartScheduled = false
        handler.removeCallbacks(restartRunnable)
        handler.removeCallbacks(discoveryTimeoutRunnable)

        val activeListener = listener
        if (registered && activeListener != null) {
            try {
                nsdManager.stopServiceDiscovery(activeListener)
            } catch (e: RuntimeException) {
                Log.w(TAG, "stopServiceDiscovery failed", e)
                registered = false
                listener = null
            }
        }
    }

    private fun startDiscovery() {
        if (!running || listener != null) return

        val newListener = DiscoveryListener(this)
        listener = newListener
        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, newListener)
        } catch (e: RuntimeException) {
            Log.w(TAG, "discoverServices failed", e)
            listener = null
            scheduleStartRetry()
        }
    }

    private fun onDiscoveryStart(source: DiscoveryListener) {
        if (listener !== source) return

        registered = true
        if (!running) {
            try {
                nsdManager.stopServiceDiscovery(source)
            } catch (e: RuntimeException) {
                Log.w(TAG, "stopServiceDiscovery after late start failed", e)
                registered = false
                listener = null
            }
            return
        }

        scheduleDiscoveryTimeout()
    }

    private fun onStartDiscoveryFailed(source: DiscoveryListener) {
        if (listener !== source) return

        registered = false
        listener = null
        handler.removeCallbacks(discoveryTimeoutRunnable)
        scheduleStartRetry()
    }

    private fun onDiscoveryStop(source: DiscoveryListener) {
        if (listener !== source) return

        registered = false
        listener = null
        handler.removeCallbacks(discoveryTimeoutRunnable)

        if (running) {
            val delay = if (pendingRestart) RESTART_DELAY_MS else 0L
            pendingRestart = false
            scheduleStartRetry(delay)
        }
    }

    private fun onStopDiscoveryFailed(source: DiscoveryListener) {
        if (listener !== source) return

        registered = false
        listener = null
        handler.removeCallbacks(discoveryTimeoutRunnable)

        if (running) {
            pendingRestart = false
            scheduleStartRetry(RESTART_DELAY_MS)
        }
    }

    private fun onServiceFound(source: DiscoveryListener, info: NsdServiceInfo) {
        if (!running || listener !== source) return

        try {
            nsdManager.resolveService(info, ResolveListener(this, source))
        } catch (e: RuntimeException) {
            Log.w(TAG, "resolveService failed for ${info.serviceName}", e)
        }
    }

    private fun onServiceLost(source: DiscoveryListener, info: NsdServiceInfo) {
        if (listener !== source || info.serviceName != serviceName) return

        serviceName = null
        resolvedHost = null
        observer.onChanged(-1)
        if (running) scheduleDiscoveryTimeout()
    }

    private fun onServiceResolved(source: DiscoveryListener, resolvedService: NsdServiceInfo) {
        if (listener !== source) return

        val host = resolvedService.host ?: return
        if (!running || !isLocalAddress(host) || !isPortInUse(host, resolvedService.port)) return

        serviceName = resolvedService.serviceName
        resolvedHost = host.hostAddress
        restartAttempts = 0
        pendingRestart = false
        restartScheduled = false
        handler.removeCallbacks(restartRunnable)
        handler.removeCallbacks(discoveryTimeoutRunnable)
        observer.onChanged(resolvedService.port)
    }

    private fun isLocalAddress(address: InetAddress): Boolean = try {
        NetworkInterface.getNetworkInterfaces()
            ?.asSequence()
            ?.any { networkInterface ->
                networkInterface.inetAddresses
                    .asSequence()
                    .any { address == it }
            } == true
    } catch (e: Exception) {
        Log.w(TAG, "Unable to enumerate local network interfaces", e)
        false
    }

    private fun isPortInUse(address: InetAddress, port: Int) = try {
        ServerSocket().use {
            it.bind(InetSocketAddress(address, port), 1)
            false
        }
    } catch (_: IOException) {
        true
    }

    private fun scheduleDiscoveryTimeout() {
        handler.removeCallbacks(discoveryTimeoutRunnable)
        if (running && serviceName == null && restartAttempts < MAX_RESTART_ATTEMPTS) {
            handler.postDelayed(discoveryTimeoutRunnable, DISCOVERY_TIMEOUT_MS)
        }
    }

    private fun scheduleRestart(reason: String) {
        if (!running || pendingRestart || restartScheduled || restartAttempts >= MAX_RESTART_ATTEMPTS) return

        Log.v(TAG, "Restarting discovery after $reason")
        pendingRestart = true
        handler.removeCallbacks(discoveryTimeoutRunnable)

        val activeListener = listener
        if (registered && activeListener != null) {
            try {
                nsdManager.stopServiceDiscovery(activeListener)
                return
            } catch (e: RuntimeException) {
                Log.w(TAG, "stopServiceDiscovery for restart failed", e)
                registered = false
                listener = null
            }
        } else {
            listener = null
        }

        pendingRestart = false
        scheduleStartRetry(RESTART_DELAY_MS)
    }

    private fun scheduleStartRetry(delayMillis: Long = START_RETRY_DELAY_MS) {
        if (!running || restartScheduled || restartAttempts >= MAX_RESTART_ATTEMPTS) return

        restartAttempts++
        restartScheduled = true
        handler.removeCallbacks(restartRunnable)
        handler.postDelayed(restartRunnable, delayMillis)
    }

    internal class DiscoveryListener(private val adbMdns: AdbMdns) : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            Log.v(TAG, "onDiscoveryStarted: $serviceType")
            adbMdns.onDiscoveryStart(this)
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "onStartDiscoveryFailed: $serviceType, $errorCode")
            adbMdns.onStartDiscoveryFailed(this)
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.v(TAG, "onDiscoveryStopped: $serviceType")
            adbMdns.onDiscoveryStop(this)
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "onStopDiscoveryFailed: $serviceType, $errorCode")
            adbMdns.onStopDiscoveryFailed(this)
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.v(TAG, "onServiceFound: ${serviceInfo.serviceName}")
            adbMdns.onServiceFound(this, serviceInfo)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            Log.v(TAG, "onServiceLost: ${serviceInfo.serviceName}")
            adbMdns.onServiceLost(this, serviceInfo)
        }
    }

    internal class ResolveListener(
        private val adbMdns: AdbMdns,
        private val discoveryListener: DiscoveryListener
    ) : NsdManager.ResolveListener {
        override fun onResolveFailed(nsdServiceInfo: NsdServiceInfo, errorCode: Int) {
            Log.v(TAG, "onResolveFailed: ${nsdServiceInfo.serviceName}, $errorCode")
        }

        override fun onServiceResolved(nsdServiceInfo: NsdServiceInfo) {
            adbMdns.onServiceResolved(discoveryListener, nsdServiceInfo)
        }
    }

    companion object {
        private const val MAX_RESTART_ATTEMPTS = 4
        private const val START_RETRY_DELAY_MS = 500L
        private const val RESTART_DELAY_MS = 250L
        private const val DISCOVERY_TIMEOUT_MS = 4_000L

        const val TLS_CONNECT = "_adb-tls-connect._tcp"
        const val TLS_PAIRING = "_adb-tls-pairing._tcp"
        const val TAG = "AdbMdns"
    }
}
