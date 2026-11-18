package com.verisonder.sonderweb.sync

/** One of your other devices and the tabs it has open (Firefox Sync). */
class RemoteDevice(val id: String, val name: String, val kind: String, val lastModified: Long, val tabs: List<RemoteTabInfo>)

class RemoteTabInfo(val title: String, val url: String, val lastUsed: Long)

/** A device a tab can be sent to. */
class SendTarget(val id: String, val name: String, val kind: String)

/** A saved address (for filling in forms). [guid] null: a new one. */
data class SavedAddress(
    val guid: String? = null,
    val name: String = "",
    val organization: String = "",
    val street: String = "",
    val suburb: String = "",
    val city: String = "",
    val region: String = "",
    val postalCode: String = "",
    val country: String = "",
    val tel: String = "",
    val email: String = "",
) {
    /** One line for lists: name, street, city. */
    val summary: String get() = listOf(name, street.lineSequence().firstOrNull().orEmpty(), city)
        .filter { it.isNotBlank() }.joinToString(", ")
}

/** A saved payment card. The full number is never kept here, only its last four digits. */
data class SavedCard(
    val guid: String? = null,
    val name: String = "",
    val last4: String = "",
    val month: Int = 0,
    val year: Int = 0,
    val type: String = "",
) {
    val summary: String get() = listOf(
        type.replaceFirstChar { it.uppercase() }.ifBlank { "Card" } + " •••• " + last4,
        if (month > 0 && year > 0) "%02d/%02d".format(month, year % 100) else "",
    ).filter { it.isNotBlank() }.joinToString(" · ")
}
