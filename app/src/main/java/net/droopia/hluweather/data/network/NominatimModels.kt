package net.droopia.hluweather.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NominatimResponse(
    val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val address: NominatimAddress? = null
)

@Serializable
data class NominatimAddress(
    val neighbourhood: String? = null,
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val municipality: String? = null,
    val hamlet: String? = null,
    val suburb: String? = null,
    @SerialName("city_district") val cityDistrict: String? = null,
    val county: String? = null,
    val state: String? = null,
    val country: String? = null
)

fun NominatimResponse.usefulPlaceName(): String? {
    val neighbourhood = firstPlaceName(address?.neighbourhood, address?.suburb)
    val townOrCity = firstPlaceName(address?.town, address?.city)
    if (neighbourhood != null && townOrCity != null &&
        !neighbourhood.equals(townOrCity, ignoreCase = true)
    ) {
        return "$neighbourhood, $townOrCity"
    }

    return firstPlaceName(
        neighbourhood,
        address?.hamlet,
        address?.village,
        address?.cityDistrict,
        townOrCity,
        address?.municipality,
        address?.county,
        address?.state,
        name,
        displayName?.substringBefore(',')
    )
}

private fun firstPlaceName(vararg names: String?): String? =
    names.firstOrNull { !it.isNullOrBlank() }?.trim()
