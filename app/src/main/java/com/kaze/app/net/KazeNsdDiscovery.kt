package com.kaze.app.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DiscoveredMac(
    val serviceName: String,
    val host: String,
    val port: Int,
    val fingerprintPrefix: String? = null
)

/**
 * Discovers KazeMac servers on local Wi-Fi via mDNS (`_kaze._tcp.`).
 * Discovery is strictly a locator; cryptographic trust is enforced by pinned P-256 keys.
 */
class KazeNsdDiscovery(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _discovered = MutableStateFlow<List<DiscoveredMac>>(emptyList())
    val discovered: StateFlow<List<DiscoveredMac>> = _discovered.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isDiscovering = false

    fun start() {
        if (isDiscovering) return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                isDiscovering = true
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.contains("_kaze._tcp")) return
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                _discovered.update { list -> list.filterNot { it.serviceName == serviceInfo.serviceName } }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                isDiscovering = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                isDiscovering = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                isDiscovering = false
            }
        }
        discoveryListener = listener
        runCatching {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    fun stop() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        isDiscovering = false
        runCatching { nsdManager.stopServiceDiscovery(listener) }
    }

    @Suppress("DEPRECATION")
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        runCatching {
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit

                override fun onServiceResolved(info: NsdServiceInfo) {
                    val hostAddr = info.host?.hostAddress ?: return
                    val port = info.port
                    if (port <= 0) return
                    val fpBytes = info.attributes["fp"]
                    val fpPrefix = fpBytes?.let { String(it, Charsets.UTF_8) }
                    val item = DiscoveredMac(
                        serviceName = info.serviceName,
                        host = hostAddr,
                        port = port,
                        fingerprintPrefix = fpPrefix
                    )
                    _discovered.update { current ->
                        (current.filterNot { it.serviceName == item.serviceName } + item)
                    }
                }
            })
        }
    }

    companion object {
        const val SERVICE_TYPE = "_kaze._tcp."
    }
}
