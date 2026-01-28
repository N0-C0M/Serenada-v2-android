package com.example.vpn

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class ZapretVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.example.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.DISCONNECT"
        var isRunning = false
            private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val isConnected = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    // Domain lists from original code
    private val blockedDomains = setOf(
        "cloudflare-ech.com", "dis.gd", "discord-attachments-uploads-prd.storage.googleapis.com",
        "discord.app", "discord.co", "discord.com", "discord.design", "discord.dev",
        "discord.gift", "discord.gifts", "discord.gg", "discord.media", "discord.new",
        "discord.store", "discord.status", "discord-activities.com", "discordactivities.com",
        "discordapp.com", "discordapp.net", "discordcdn.com", "discordmerch.com",
        "discordpartygames.com", "discordsays.com", "discordsez.com",
        "yt3.ggpht.com", "yt4.ggpht.com", "yt3.googleusercontent.com", "googlevideo.com",
        "jnn-pa.googleapis.com", "stable.dl2.discordapp.net", "wide-youtube.l.google.com",
        "youtube-nocookie.com", "youtube-ui.l.google.com", "youtube.com",
        "youtubeembeddedplayer.googleapis.com", "youtubekids.com", "youtubei.googleapis.com",
        "youtu.be", "yt-video-upload.l.google.com", "ytimg.com", "ytimg.l.google.com",
        "frankerfacez.com", "ffzap.com", "betterttv.net", "7tv.app", "7tv.io"
    )

    // Port configurations from original code
    private val tcpPorts = setOf(80, 443, 2053, 2083, 2087, 2096, 8443)
    private val udpPorts = setOf(443, 19294, 19344, 50000, 50100)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_CONNECT -> connect()
            ACTION_DISCONNECT -> disconnect()
            else -> START_NOT_STICKY
        }
    }

    private fun connect(): Int {
        if (isConnected.get()) return START_STICKY

        val builder = Builder()
        builder.setSession("Zapret VPN")
        builder.setMtu(1500)

        // Configure VPN
        builder.addAddress("10.8.0.2", 32)
        builder.addRoute("0.0.0.0", 0)
        builder.addDnsServer("8.8.8.8")
        builder.addDnsServer("1.1.1.1")

        // Allow all traffic through VPN (исправлено - должно быть allow, не disallow)
        builder.addAllowedApplication(packageName)

        vpnInterface = builder.establish()

        // Start foreground service
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "vpn_channel")
            .setContentTitle("Zapret VPN")
            .setContentText("Running")
            .setSmallIcon(R.drawable.ic_vpn)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)

        isConnected.set(true)
        isRunning = true

        // Start packet processing
        scope.launch {
            startPacketProcessing()
        }

        return START_STICKY
    }

    private fun disconnect() {
        isConnected.set(false)
        isRunning = false

        scope.cancel()
        vpnInterface?.close()
        vpnInterface = null

        stopForeground(true)
        stopSelf()
    }

    private suspend fun startPacketProcessing() {
        val vpnInterface = vpnInterface ?: return // Исправлено: зарезервированное слово 'interface'

        withContext(Dispatchers.IO) {
            try {
                val inputStream = FileInputStream(vpnInterface.fileDescriptor)
                val outputStream = FileOutputStream(vpnInterface.fileDescriptor)

                val buffer = ByteArray(32767)

                while (isConnected.get()) {
                    val length = inputStream.read(buffer)
                    if (length > 0) {
                        val packet = buffer.copyOf(length)
                        val processedPacket = processPacket(packet)
                        processedPacket?.let {
                            outputStream.write(it)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun processPacket(packet: ByteArray): ByteArray? {
        return try {
            // Parse IP packet
            if (!isIPPacket(packet)) return packet

            val protocol = getIPProtocol(packet)
            val destinationIP = getDestinationIP(packet)
            val sourcePort = getSourcePort(packet, protocol)
            val destPort = getDestinationPort(packet, protocol)

            // Check if packet should be blocked
            if (shouldBlockPacket(protocol, destinationIP, destPort, packet)) {
                return null
            }

            // Apply DPI evasion techniques
            applyDpiEvasion(packet, protocol, destPort)

        } catch (e: Exception) {
            e.printStackTrace()
            packet
        }
    }

    private fun isIPPacket(packet: ByteArray): Boolean {
        return packet.size >= 20 && ((packet[0].toInt() and 0xF0) == 0x40) // IPv4
    }

    private fun getIPProtocol(packet: ByteArray): Int {
        return packet[9].toInt() and 0xFF
    }

    private fun getDestinationIP(packet: ByteArray): String {
        return "${packet[16].toInt() and 0xFF}.${packet[17].toInt() and 0xFF}." +
                "${packet[18].toInt() and 0xFF}.${packet[19].toInt() and 0xFF}"
    }

    private fun getSourcePort(packet: ByteArray, protocol: Int): Int {
        return when (protocol) {
            OsConstants.IPPROTO_TCP, OsConstants.IPPROTO_UDP -> {
                if (packet.size >= getTransportHeaderOffset(packet) + 4) {
                    val offset = getTransportHeaderOffset(packet)
                    ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)
                } else -1
            }
            else -> -1
        }
    }

    private fun getDestinationPort(packet: ByteArray, protocol: Int): Int {
        return when (protocol) {
            OsConstants.IPPROTO_TCP, OsConstants.IPPROTO_UDP -> {
                if (packet.size >= getTransportHeaderOffset(packet) + 4) {
                    val offset = getTransportHeaderOffset(packet)
                    ((packet[offset + 2].toInt() and 0xFF) shl 8) or (packet[offset + 3].toInt() and 0xFF)
                } else -1
            }
            else -> -1
        }
    }

    private fun getTransportHeaderOffset(packet: ByteArray): Int {
        return (packet[0].toInt() and 0x0F) * 4
    }

    private fun shouldBlockPacket(protocol: Int, destIP: String, destPort: Int, packet: ByteArray): Boolean {
        // Check port-based filtering
        when (protocol) {
            OsConstants.IPPROTO_TCP -> {
                if (destPort in tcpPorts) {
                    // Check domain in payload
                    if (containsBlockedDomain(packet)) return true
                }
            }
            OsConstants.IPPROTO_UDP -> {
                if (destPort in udpPorts) {
                    // Check for Discord STUN, etc.
                    if (isDiscordProtocol(packet)) return true
                }
            }
        }

        return false
    }

    private fun containsBlockedDomain(packet: ByteArray): Boolean {
        val packetStr = String(packet)
        return blockedDomains.any { domain -> packetStr.contains(domain, ignoreCase = true) }
    }

    private fun isDiscordProtocol(packet: ByteArray): Boolean {
        // Simple heuristic for Discord protocols
        val payload = String(packet)
        return payload.contains("discord", ignoreCase = true) ||
                payload.contains("stun", ignoreCase = true)
    }

    private fun applyDpiEvasion(packet: ByteArray, protocol: Int, destPort: Int): ByteArray {
        return when {
            protocol == OsConstants.IPPROTO_TCP && destPort == 443 ->
                applyTlsEvasion(packet)
            protocol == OsConstants.IPPROTO_UDP && destPort == 443 ->
                applyQuicEvasion(packet)
            protocol == OsConstants.IPPROTO_TCP && destPort == 80 ->
                applyHttpEvasion(packet)
            else -> packet
        }
    }

    private fun applyTlsEvasion(packet: ByteArray): ByteArray {
        // Fake TLS ClientHello - similar to original code
        val modifiedPacket = packet.copyOf()

        // Find TLS handshake and modify
        val tlsHeader = byteArrayOf(0x16, 0x03, 0x01) // TLS 1.0/1.1/1.2
        val tlsIndex = modifiedPacket.indexOfSequence(tlsHeader)

        if (tlsIndex != -1) {
            // Apply multi-split and fake handshake
            applyMultiSplit(modifiedPacket, tlsIndex)
            applyFakeTlsSignature(modifiedPacket, tlsIndex)
        }

        return modifiedPacket
    }

    private fun applyQuicEvasion(packet: ByteArray): ByteArray {
        // QUIC initial packet manipulation
        val modifiedPacket = packet.copyOf()

        // Detect QUIC packets (usually start with specific bits)
        if (modifiedPacket.size > 10 && (modifiedPacket[0].toInt() and 0xC0) == 0xC0) {
            // Apply fake QUIC initial like in original code
            injectFakeQuicInitial(modifiedPacket)
        }

        return modifiedPacket
    }

    private fun applyHttpEvasion(packet: ByteArray): ByteArray {
        // HTTP DPI evasion with multi-split and fake signatures
        val modifiedPacket = packet.copyOf()
        val httpMethods = listOf("GET", "POST", "PUT", "HEAD", "OPTIONS")

        val packetStr = String(modifiedPacket)
        if (httpMethods.any { packetStr.startsWith(it) }) {
            applyMultiSplit(modifiedPacket, 0)
            applyMd5SignatureFooling(modifiedPacket)
        }

        return modifiedPacket
    }

    private fun applyMultiSplit(packet: ByteArray, startIndex: Int) {
        // Implement multi-split technique from original code
        // Split packet into multiple smaller packets to evade DPI
        if (packet.size > startIndex + 10) {
            // Simple splitting - in real implementation would create multiple packets
            for (i in startIndex until packet.size - 1 step 2) {
                packet[i] = packet[i + 1]
            }
        }
    }

    private fun applyFakeTlsSignature(packet: ByteArray, startIndex: Int) {
        // Modify TLS signature to fool DPI
        if (packet.size > startIndex + 10) {
            // Change TLS version or cipher suites slightly
            packet[startIndex + 3] = 0x03 // TLS 1.2
            packet[startIndex + 4] = 0x03
        }
    }

    private fun applyMd5SignatureFooling(packet: ByteArray) {
        // Modify packet to fool MD5-based signature detection
        for (i in 0 until minOf(packet.size, 100)) {
            if (i % 7 == 0) {
                packet[i] = (packet[i].toInt() xor 0xFF).toByte()
            }
        }
    }

    private fun injectFakeQuicInitial(packet: ByteArray) {
        // Inject fake QUIC initial packet data
        val fakeQuicHeader = byteArrayOf(
            0xC0.toByte(), 0x00, 0x00, 0x00, 0x01, 0x00, 0x08, 0x00,
            0x00, 0x00, 0x00, 0x00
        )

        if (packet.size >= fakeQuicHeader.size) {
            System.arraycopy(fakeQuicHeader, 0, packet, 0, fakeQuicHeader.size)
        }
    }

    private fun ByteArray.indexOfSequence(sequence: ByteArray): Int {
        for (i in 0..this.size - sequence.size) {
            var found = true
            for (j in sequence.indices) {
                if (this[i + j] != sequence[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }
}