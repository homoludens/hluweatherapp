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

fun NominatimResponse.usefulPlaceName(): String? = sequenceOf(
    address?.city,
    address?.town,
    address?.village,
    address?.municipality,
    address?.hamlet,
    address?.suburb,
    address?.cityDistrict,
    address?.county,
    address?.state,
    name,
    displayName?.substringBefore(',')
).firstOrNull { !it.isNullOrBlank() }?.trim()
