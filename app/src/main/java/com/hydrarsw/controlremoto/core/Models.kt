package com.hydrarsw.controlremoto.core

import org.json.JSONObject
import java.util.UUID

enum class TvKind(val label: String) {
    ANDROID_TV("Android TV / Google TV"),
    SAMSUNG("Samsung Smart TV"),
    LG("LG webOS"),
    INFRARED("Infrarrojo"),
}

enum class RemoteKey {
    POWER, VOL_UP, VOL_DOWN, MUTE, CH_UP, CH_DOWN,
    UP, DOWN, LEFT, RIGHT, OK, BACK, HOME, MENU, INPUT,
    PLAY_PAUSE, STOP, REWIND, FORWARD,
    NUM_0, NUM_1, NUM_2, NUM_3, NUM_4, NUM_5, NUM_6, NUM_7, NUM_8, NUM_9,
}

data class SavedTv(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: TvKind,
    val host: String = "",
    val mac: String = "",
    val token: String = "",
    val pinnedCert: String = "",
    val irBrand: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("kind", kind.name)
        .put("host", host).put("mac", mac).put("token", token)
        .put("pinnedCert", pinnedCert).put("irBrand", irBrand)

    companion object {
        fun fromJson(o: JSONObject) = SavedTv(
            id = o.getString("id"),
            name = o.getString("name"),
            kind = TvKind.valueOf(o.getString("kind")),
            host = o.optString("host"),
            mac = o.optString("mac"),
            token = o.optString("token"),
            pinnedCert = o.optString("pinnedCert"),
            irBrand = o.optString("irBrand"),
        )
    }
}

sealed interface ConnectionState {
    data object Connecting : ConnectionState
    data class WaitingForTv(val message: String) : ConnectionState
    data object Ready : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

/** Una conexión activa con una televisión. Las implementaciones nunca bloquean en [send]. */
interface TvClient {
    val supportedKeys: Set<RemoteKey>
    fun connect()
    fun send(key: RemoteKey)
    fun close()
}

interface TvClientListener {
    fun onState(state: ConnectionState)
    /** La TV entregó datos persistentes: token, certificado o MAC. */
    fun onTvUpdated(update: (SavedTv) -> SavedTv)
}
