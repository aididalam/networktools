package com.nexdevlab.networktools.data

enum class WhoisQueryType(val label: String) {
    DOMAIN("Domain"),
    IP_ADDRESS("IP address"),
}

enum class WhoisServerMode(val label: String) {
    AUTOMATIC("Automatic"),
    MANUAL("Manual"),
}

data class WhoisResult(
    val query: String,
    val queryType: WhoisQueryType,
    val servers: List<String>,
    val registrar: String? = null,
    val registrationDate: String? = null,
    val expirationDate: String? = null,
    val nameServers: List<String> = emptyList(),
    val registrant: String? = null,
    val organization: String? = null,
    val ipBlockOwner: String? = null,
    val autonomousSystem: String? = null,
    val rawResponse: String,
) {
    fun asPlainText(): String = buildList {
        add("WHOIS lookup")
        add("Query: $query")
        add("Type: ${queryType.label}")
        add("WHOIS server: ${servers.joinToString(" -> ")}")
        addValue("Registrar", registrar)
        addValue("Registration date", registrationDate)
        addValue("Expiration date", expirationDate)
        addValue("Name servers", nameServers.takeIf { it.isNotEmpty() }?.joinToString())
        addValue("Registrant", registrant)
        addValue("Organization", organization)
        addValue("IP block owner", ipBlockOwner)
        addValue("Autonomous system", autonomousSystem)
    }.joinToString("\n")

    private fun MutableList<String>.addValue(label: String, value: String?) {
        if (!value.isNullOrBlank()) add("$label: $value")
    }
}

data class WhoisUiState(
    val loading: Boolean = false,
    val result: WhoisResult? = null,
    val error: String? = null,
)
