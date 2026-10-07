package io.github.ar13x3.airpoint.net

import android.os.SystemClock
import io.github.ar13x3.airpoint.core.DiscoveredPc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/**
 * Finds Airpoint desktops on the local network: broadcast a small JSON "discover" datagram,
 * collect the "announce" replies. The reply's source address is used as the host, because
 * it is reachable from here by definition (a PC can have several adapters).
 */
object Discovery {
    private const val ROUND_MS = 1_600L
    private const val FORGET_AFTER_MS = 5_000L

    /** Emits the current list of PCs, refreshed every round, until cancelled. */
    fun scan(port: Int = DEFAULT_PORT): Flow<List<DiscoveredPc>> = flow {
        val seen = LinkedHashMap<String, Pair<DiscoveredPc, Long>>()
        val payload = JSONObject().put("type", "airpoint.discover").put("v", PROTOCOL_VERSION)
            .toString().toByteArray()
        val buffer = ByteArray(1024)

        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 200
            emit(emptyList())
            while (currentCoroutineContext().isActive) {
                for (address in broadcastAddresses()) {
                    runCatching { socket.send(DatagramPacket(payload, payload.size, address, port)) }
                }
                val roundEnd = SystemClock.elapsedRealtime() + ROUND_MS
                while (SystemClock.elapsedRealtime() < roundEnd && currentCoroutineContext().isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val pc = parse(packet) ?: continue
                    val isNew = pc.id !in seen
                    seen[pc.id] = pc to SystemClock.elapsedRealtime()
                    if (isNew) emit(seen.values.map { it.first })
                }
                val now = SystemClock.elapsedRealtime()
                seen.entries.removeAll { now - it.value.second > FORGET_AFTER_MS }
                emit(seen.values.map { it.first })
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Look for one specific PC (e.g. after its IP changed). */
    suspend fun find(pcId: String, timeoutMs: Long = 2_500): DiscoveredPc? = withTimeoutOrNull(timeoutMs) {
        scan().mapNotNull { list -> list.firstOrNull { it.id == pcId } }.first()
    }

    private fun parse(packet: DatagramPacket): DiscoveredPc? = runCatching {
        val o = JSONObject(String(packet.data, packet.offset, packet.length, Charsets.UTF_8))
        if (o.optString("type") != "airpoint.announce") return null
        DiscoveredPc(
            id = o.getString("id"),
            name = o.optString("name").ifBlank { "Computer" },
            host = packet.address.hostAddress ?: return null,
            port = o.optInt("port", DEFAULT_PORT),
            version = o.optString("version"),
        )
    }.getOrNull()

    private fun broadcastAddresses(): Set<InetAddress> {
        val out = linkedSetOf<InetAddress>(InetAddress.getByName("255.255.255.255"))
        runCatching {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                nif.interfaceAddresses
                    .filter { it.address is Inet4Address }
                    .mapNotNullTo(out) { it.broadcast }
            }
        }
        return out
    }
}
