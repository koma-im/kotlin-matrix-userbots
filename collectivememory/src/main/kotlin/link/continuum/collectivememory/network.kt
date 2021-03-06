package link.continuum.collectivememory

import java.net.InetSocketAddress
import java.net.Proxy

fun parseProxy(str: String): Proxy? {
    val proxyconf = str.split(" ", limit = 3)
    if (proxyconf.size < 1 ) return null
    if (proxyconf[0] == "DIRECT" ) return Proxy.NO_PROXY
    if (proxyconf.size < 3 ) return null
    val type = when(proxyconf[0]) {
        "HTTP" -> Proxy.Type.HTTP
        "SOCKS" -> Proxy.Type.SOCKS
        else -> return null
    }
    val host = proxyconf[1]
    val port = proxyconf[2].toIntOrNull()
    if (port == null) return null
    val sa = InetSocketAddress.createUnresolved(host, port)
    return Proxy(type, sa)
}
