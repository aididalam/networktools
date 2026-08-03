package com.nexdevlab.networktools.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class IpInformationRepository(private val context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    suspend fun load(includeWifiDetails: Boolean): NetworkSnapshot = withContext(Dispatchers.IO) {
        val network = connectivityManager.activeNetwork
        val links = network?.let(connectivityManager::getLinkProperties)
        val capabilities = network?.let(connectivityManager::getNetworkCapabilities)
        val localAddresses = links?.linkAddresses.orEmpty()
            .map { it.address }
            .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }
            .map(::displayAddress)
            .distinct()
        val primaryIpv4 = links?.linkAddresses.orEmpty()
            .firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
        val wifiInfo = if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            readWifiInfo()
        } else {
            null
        }
        val canReadProtectedWifi = includeWifiDetails && hasWifiDetailsPermission()
        val dnsServers = links?.dnsServers.orEmpty().map(::displayAddress)
        val gateway = links?.routes.orEmpty()
            .firstOrNull { it.isDefaultRoute && it.gateway != null }
            ?.gateway
            ?.let(::displayAddress)

        var publicIp: String? = null
        var organization: String? = null
        var asn: String? = null
        var remoteError: String? = null
        if (network != null && capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
            runCatching {
                publicIp = fetchPublicIp()
                ensureActive()
                val registration = fetchRegistration(publicIp!!)
                organization = registration.organization
                asn = queryOriginAsn(publicIp!!, links?.dnsServers.orEmpty())
            }.onFailure {
                remoteError = "Internet details are temporarily unavailable"
            }
        }

        NetworkSnapshot(
            publicIp = publicIp,
            localAddresses = localAddresses,
            wifiSsid = wifiInfo?.takeIf { canReadProtectedWifi }?.ssid?.cleanSsid(),
            wifiBssid = wifiInfo?.takeIf { canReadProtectedWifi }?.bssid?.validBssid(),
            gateway = gateway,
            subnetMask = primaryIpv4?.let { ipv4SubnetMask(it.prefixLength) },
            broadcastAddress = primaryIpv4?.let(::ipv4BroadcastAddress),
            dnsServers = dnsServers,
            wifiSignal = wifiInfo?.let { "${it.rssi} dBm" },
            wifiLinkSpeed = wifiInfo?.let { "${it.linkSpeed} Mbps" },
            organization = organization,
            asn = asn,
            connectionType = connectionType(capabilities),
            remoteError = remoteError,
        )
    }

    private fun hasWifiDetailsPermission(): Boolean {
        val locationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val nearbyGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES,
            ) == PackageManager.PERMISSION_GRANTED
        return locationGranted && nearbyGranted
    }

    @Suppress("DEPRECATION")
    private fun readWifiInfo(): WifiInfo? =
        wifiManager.connectionInfo

    private fun connectionType(capabilities: NetworkCapabilities?): String = when {
        capabilities == null -> "Offline"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
        else -> "Other network"
    }

    private fun fetchPublicIp(): String {
        val response = getText("https://api.ipify.org", 128).trim()
        require(isNumericIp(response)) { "Invalid IP response" }
        return displayAddress(InetAddress.getByName(response))
    }

    private fun fetchRegistration(ip: String): Registration {
        val address = InetAddress.getByName(ip)
        val bootstrapUrl = if (address is Inet4Address) {
            "https://data.iana.org/rdap/ipv4.json"
        } else {
            "https://data.iana.org/rdap/ipv6.json"
        }
        val bootstrap = JSONObject(getText(bootstrapUrl, 768 * 1024))
        val services = bootstrap.getJSONArray("services")
        var rdapBase: String? = null
        loop@ for (index in 0 until services.length()) {
            val service = services.getJSONArray(index)
            val prefixes = service.getJSONArray(0)
            for (prefixIndex in 0 until prefixes.length()) {
                if (addressInPrefix(address, prefixes.getString(prefixIndex))) {
                    rdapBase = service.getJSONArray(1).optString(0).takeIf { it.startsWith("https://") }
                    break@loop
                }
            }
        }
        requireNotNull(rdapBase) { "No authoritative RDAP service" }
        val rdapUrl = rdapBase.trimEnd('/') + "/ip/" + ip
        val response = JSONObject(getText(rdapUrl, 1024 * 1024))
        return Registration(
            organization = findOrganization(response)
                ?: response.optString("name").takeIf(String::isNotBlank),
        )
    }

    private fun findOrganization(response: JSONObject): String? {
        val entities = response.optJSONArray("entities") ?: return null
        for (index in 0 until entities.length()) {
            val entity = entities.optJSONObject(index) ?: continue
            val roles = entity.optJSONArray("roles") ?: JSONArray()
            val preferred = (0 until roles.length()).any {
                roles.optString(it) in setOf("registrant", "administrative", "technical")
            }
            val name = vCardName(entity.optJSONArray("vcardArray"))
            if (preferred && !name.isNullOrBlank()) return name
        }
        for (index in 0 until entities.length()) {
            vCardName(entities.optJSONObject(index)?.optJSONArray("vcardArray"))?.let { return it }
        }
        return null
    }

    private fun vCardName(vcard: JSONArray?): String? {
        val properties = vcard?.optJSONArray(1) ?: return null
        for (wanted in listOf("org", "fn")) {
            for (index in 0 until properties.length()) {
                val property = properties.optJSONArray(index) ?: continue
                if (property.optString(0) == wanted) {
                    val value = property.opt(3)
                    return when (value) {
                        is String -> value
                        is JSONArray -> (0 until value.length())
                            .map(value::optString)
                            .filter(String::isNotBlank)
                            .joinToString(" ")
                        else -> null
                    }?.takeIf(String::isNotBlank)
                }
            }
        }
        return null
    }

    private fun queryOriginAsn(ip: String, dnsServers: List<InetAddress>): String? {
        val server = dnsServers.firstOrNull() ?: return null
        val address = InetAddress.getByName(ip)
        val queryName = when (address) {
            is Inet4Address -> address.hostAddress.orEmpty().split('.').reversed().joinToString(".") +
                ".origin.asn.cymru.com"
            is Inet6Address -> address.address.joinToString("") { "%02x".format(it.toInt() and 0xff) }
                .reversed()
                .toCharArray()
                .joinToString(".") + ".origin6.asn.cymru.com"
            else -> return null
        }
        val answer = dnsTxtQuery(queryName, server) ?: return null
        val number = answer.substringBefore('|').trim().substringBefore(' ')
        return number.takeIf { it.all(Char::isDigit) }?.let { "AS$it" }
    }

    private fun dnsTxtQuery(host: String, server: InetAddress): String? {
        val id = SecureRandom().nextInt(65536)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeShort(id)
            data.writeShort(0x0100)
            data.writeShort(1)
            data.writeShort(0)
            data.writeShort(0)
            data.writeShort(0)
            host.split('.').forEach { label ->
                val bytes = label.toByteArray(StandardCharsets.US_ASCII)
                require(bytes.size in 1..63)
                data.writeByte(bytes.size)
                data.write(bytes)
            }
            data.writeByte(0)
            data.writeShort(16)
            data.writeShort(1)
        }
        val response = ByteArray(4096)
        DatagramSocket().use { socket ->
            socket.soTimeout = 3_000
            socket.send(DatagramPacket(output.toByteArray(), output.size(), server, 53))
            val packet = DatagramPacket(response, response.size)
            socket.receive(packet)
            return parseTxtResponse(response.copyOf(packet.length), id)
        }
    }

    private fun parseTxtResponse(bytes: ByteArray, expectedId: Int): String? {
        if (bytes.size < 12 || readUnsignedShort(bytes, 0) != expectedId) return null
        val questionCount = readUnsignedShort(bytes, 4)
        val answerCount = readUnsignedShort(bytes, 6)
        var offset = 12
        repeat(questionCount) {
            offset = skipDnsName(bytes, offset)
            offset += 4
        }
        repeat(answerCount) {
            offset = skipDnsName(bytes, offset)
            if (offset + 10 > bytes.size) return null
            val type = readUnsignedShort(bytes, offset)
            val dataLength = readUnsignedShort(bytes, offset + 8)
            offset += 10
            if (offset + dataLength > bytes.size) return null
            if (type == 16 && dataLength > 1) {
                val end = offset + dataLength
                val text = StringBuilder()
                while (offset < end) {
                    val length = bytes[offset].toInt() and 0xff
                    offset++
                    if (offset + length > end) return null
                    text.append(String(bytes, offset, length, StandardCharsets.UTF_8))
                    offset += length
                }
                return text.toString().trim('"')
            }
            offset += dataLength
        }
        return null
    }

    private fun skipDnsName(bytes: ByteArray, initialOffset: Int): Int {
        var offset = initialOffset
        while (offset < bytes.size) {
            val length = bytes[offset].toInt() and 0xff
            if (length == 0) return offset + 1
            if (length and 0xc0 == 0xc0) return offset + 2
            offset += length + 1
        }
        return bytes.size
    }

    private fun getText(url: String, maxBytes: Int): String {
        val connection = URL(url).openConnection() as HttpsURLConnection
        return try {
            connection.connectTimeout = 4_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json, text/plain")
            connection.setRequestProperty("User-Agent", "NetworkTools/1.0")
            val code = connection.responseCode
            require(code in 200..299) { "HTTP $code" }
            val declaredLength = connection.contentLengthLong
            require(declaredLength <= maxBytes || declaredLength == -1L) { "Response too large" }
            val buffer = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val block = ByteArray(8192)
                while (true) {
                    val count = input.read(block)
                    if (count < 0) break
                    require(buffer.size() + count <= maxBytes) { "Response too large" }
                    buffer.write(block, 0, count)
                }
            }
            buffer.toString(StandardCharsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }

    private data class Registration(val organization: String?)

    companion object {
        internal fun ipv4SubnetMask(prefixLength: Int): String? {
            if (prefixLength !in 0..32) return null
            val mask = if (prefixLength == 0) 0 else -1 shl (32 - prefixLength)
            return listOf(24, 16, 8, 0).joinToString(".") { shift ->
                ((mask ushr shift) and 0xff).toString()
            }
        }

        internal fun ipv4BroadcastAddress(linkAddress: LinkAddress): String? {
            val address = linkAddress.address as? Inet4Address ?: return null
            return ipv4BroadcastAddress(address.address, linkAddress.prefixLength)
        }

        internal fun ipv4BroadcastAddress(address: ByteArray, prefix: Int): String? {
            if (address.size != 4) return null
            if (prefix !in 0..32) return null
            val ip = address.fold(0) { result, byte ->
                (result shl 8) or (byte.toInt() and 0xff)
            }
            val mask = if (prefix == 0) 0 else -1 shl (32 - prefix)
            val broadcast = ip or mask.inv()
            return listOf(24, 16, 8, 0).joinToString(".") { shift ->
                ((broadcast ushr shift) and 0xff).toString()
            }
        }

        private fun addressInPrefix(address: InetAddress, prefix: String): Boolean {
            val parts = prefix.split('/', limit = 2)
            if (parts.size != 2) return false
            val network = runCatching { InetAddress.getByName(parts[0]) }.getOrNull() ?: return false
            val bits = parts[1].toIntOrNull() ?: return false
            val addressBytes = address.address
            val networkBytes = network.address
            if (addressBytes.size != networkBytes.size || bits !in 0..addressBytes.size * 8) return false
            val wholeBytes = bits / 8
            val remainingBits = bits % 8
            for (index in 0 until wholeBytes) {
                if (addressBytes[index] != networkBytes[index]) return false
            }
            if (remainingBits == 0) return true
            val mask = (0xff shl (8 - remainingBits)) and 0xff
            return (addressBytes[wholeBytes].toInt() and mask) ==
                (networkBytes[wholeBytes].toInt() and mask)
        }

        private fun isNumericIp(value: String): Boolean {
            if (value.isBlank() || value.length > 45 || value.any(Char::isWhitespace)) return false
            if (':' in value) return value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it in ":." }
            val parts = value.split('.')
            return parts.size == 4 && parts.all { part ->
                part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) &&
                    (part.toIntOrNull() ?: 256) in 0..255
            }
        }

        private fun displayAddress(address: InetAddress): String =
            address.hostAddress.orEmpty().substringBefore('%')

        private fun String.cleanSsid(): String? = trim('"')
            .takeUnless { it.isBlank() || it == WifiManager.UNKNOWN_SSID }

        private fun String.validBssid(): String? =
            takeUnless { it.isBlank() || it == "02:00:00:00:00:00" }

        private fun readUnsignedShort(bytes: ByteArray, offset: Int): Int =
            ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)
    }
}
