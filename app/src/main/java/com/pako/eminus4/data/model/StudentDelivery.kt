package com.pako.eminus4.data.model

import com.google.gson.annotations.SerializedName

data class StudentDelivery(
    @SerializedName("idActividad")               val idActividad: Long = 0,
    @SerializedName("idActividadEntrega")        val idActividadEntrega: Long = 0,
    @SerializedName("fechaEntrega")              val fechaEntrega: String? = null,
    @SerializedName("descEntrega")               val descEntrega: String? = null,
    @SerializedName("idCurso")                   val idCurso: Long = 0,
    @SerializedName("idActividadRevision")       val idActividadRevision: Long? = null,
    @SerializedName("descRevision")              val descRevision: String? = null,
    @SerializedName("calificacion")              val calificacion: Double? = null,
    @SerializedName("idEstado")                  val idEstado: Int = 0,
    @SerializedName("fechaRevision")             val fechaRevision: String? = null,
    @SerializedName("archivosEntregaConTamano")  val archivos: List<DeliveryAttachment>? = null,
    @SerializedName("archivosRevisionConTamano") val archivosRevision: List<DeliveryAttachment>? = null
)

