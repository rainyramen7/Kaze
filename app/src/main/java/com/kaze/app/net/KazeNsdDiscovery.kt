package com.kaze.app.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.ArrayDeque

data class DiscoveredMac(
    val serviceName: String,
    val host: String,
    val port: Int,
    val fingerprintPrefix: String? = null
)

/**
 * Discovers KazeMac servers on local Wi-Fi via mDNS (`_kaze._tcp.`).
 * Discovery is strictly a locator; cryptographic trust is enforced by pinned P-256 keys.
 * Synchronized to prevent duplicate listener leaks and serializes resolveService calls cleanly.
 */
class KazeNsdDiscovery(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _discovered = MutableStateFlow<List<DiscoveredMac>>(emptyList())
    val discovered: StateFlow<List<DiscoveredMac>> = _discovered.asStateFlow()

    private val lock = Any()
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private val queuedNames = mutableSetOf<String>()
    private var isResolving = false

    fun start() {
        val listenerToRegister = synchronized(lock) {
            if (discoveryListener != null) return
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) = Unit

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (!serviceInfo.serviceType.contains("_kaze._tcp")) return
                    enqueueResolve(serviceInfo)
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    _discovered.update { list -> list.filterNot { it.serviceName == serviceInfo.serviceName } }
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    synchronized(lock) {
                        if (discoveryListener === this) {
                            discoveryListener = null
                        }
                    }
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    synchronized(lock) {
                        if (discoveryListener === this) {
                            discoveryListener = null
                        }
                    }
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    synchronized(lock) {
                        if (discoveryListener === this) {
                            discoveryListener = null
                        }
                    }
                }
            }
            discoveryListener = listener
            listener
        }

        runCatching {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listenerToRegister)
        }.onFailure {
            synchronized(lock) {
                if (discoveryListener === listenerToRegister) {
                    discoveryListener = null
                }
            }
        }
    }

    fun stop() {
        val listener = synchronized(lock) {
            resolveQueue.clear()
            queuedNames.clear()
            isResolving = false
            val current = discoveryListener
            discoveryListener = null
            current
        } ?: return

        runCatching { nsdManager.stopServiceDiscovery(listener) }
    }

    private fun enqueueResolve(serviceInfo: NsdServiceInfo) {
        val next = synchronized(lock) {
            if (discoveryListener == null) return
            if (!queuedNames.add(serviceInfo.serviceName)) return
            resolveQueue.addLast(serviceInfo)
            if (isResolving) return
            isResolving = true
            resolveQueue.removeFirst()
        }
        resolveNext(next)
    }

    @Suppress("DEPRECATION")
    private fun resolveNext(serviceInfo: NsdServiceInfo) {
        runCatching {
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    finishAndPumpNext(serviceInfo.serviceName)
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    val hostAddr = info.host?.hostAddress
                    val port = info.port
                    if (hostAddr != null && port > 0) {
                        val fpBytes = info.attributes["fp"]
                        val fpPrefix = fpBytes?.let { String(it, Charsets.UTF_8) }
                        val item = DiscoveredMac(
                            serviceName = info.serviceName,
                            host = hostAddr,
                            port = port,
                            fingerprintPrefix = fpPrefix
                        )
                        _discovered.update { current ->
                            current.filterNot { it.serviceName == item.serviceName } + item
                        }
                    }
                    finishAndPumpNext(serviceInfo.serviceName)
                }
            })
        }.onFailure {
            finishAndPumpNext(serviceInfo.serviceName)
        }
    }

    private fun finishAndPumpNext(completedName: String) {
        val next = synchronized(lock) {
            queuedNames.remove(completedName)
            if (discoveryListener == null || resolveQueue.isEmpty()) {
                isResolving = false
                null
            } else {
                resolveQueue.removeFirst()
            }
        }
        if (next != null) {
            resolveNext(next)
        }
    }

    companion object {
        const val SERVICE_TYPE = "_kaze._tcp."
    }
}
