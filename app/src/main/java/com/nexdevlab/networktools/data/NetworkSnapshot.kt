package com.nexdevlab.networktools.data

data class NetworkSnapshot(
    val publicIp: String? = null,
    val localAddresses: List<String> = emptyList(),
    val wifiSsid: String? = null,
    val wifiBssid: String? = null,
    val gateway: String? = null,
    val subnetMask: String? = null,
    val broadcastAddress: String? = null,
    val dnsServers: List<String> = emptyList(),
    val wifiSignal: String? = null,
    val wifiLinkSpeed: String? = null,
    val organization: String? = null,
    val asn: String? = null,
    val connectionType: String = "Offline",
    val refreshedAtMillis: Long = System.currentTimeMillis(),
    val remoteError: String? = null,
) {
    fun asPlainText(): String = buildList {
        add("Network information")
        addValue("Public IP", publicIp)
        addValue("Local IP", localAddresses.takeIf { it.isNotEmpty() }?.joinToString())
        addValue("Wi-Fi SSID", wifiSsid)
        addValue("Wi-Fi BSSID", wifiBssid)
        addValue("Gateway", gateway)
        addValue("Subnet mask", subnetMask)
        addValue("Broadcast", broadcastAddress)
        addValue("DNS servers", dnsServers.takeIf { it.isNotEmpty() }?.joinToString())
        addValue("Wi-Fi signal", wifiSignal)
        addValue("Wi-Fi link speed", wifiLinkSpeed)
        addValue("Network organization", organization)
        addValue("ASN", asn)
        addValue("Connection type", connectionType)
    }.joinToString("\n")

    private fun MutableList<String>.addValue(label: String, value: String?) {
        if (!value.isNullOrBlank()) add("$label: $value")
    }
}

data class IpInformationUiState(
    val loading: Boolean = false,
    val snapshot: NetworkSnapshot? = null,
    val error: String? = null,
)
