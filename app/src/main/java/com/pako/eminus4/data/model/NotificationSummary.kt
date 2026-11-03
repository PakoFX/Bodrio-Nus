package com.pako.eminus4.data.model

import com.google.gson.annotations.SerializedName

data class NotificationSummary(
    @SerializedName("msjNuevos") val msjNuevos: Int = 0,
    @SerializedName("actNuevas") val actNuevas: Int = 0,
    @SerializedName("actActivas") val actActivas: Int = 0,
    @SerializedName("frNuevos") val frNuevos: Int = 0,
    @SerializedName("frActivos") val frActivos: Int = 0,
    @SerializedName("exNuevos") val exNuevos: Int = 0,
    @SerializedName("exActivos") val exActivos: Int = 0
) {
    fun getTotalAlertas(): Int {
        return msjNuevos + actNuevas + frNuevos + exNuevos
    }
}

