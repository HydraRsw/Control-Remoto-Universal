package com.hydrarsw.controlremoto.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import com.hydrarsw.controlremoto.core.ConnectionState
import com.hydrarsw.controlremoto.core.RemoteKey
import com.hydrarsw.controlremoto.core.TvClient
import com.hydrarsw.controlremoto.core.TvClientListener
import org.json.JSONObject
import java.util.concurrent.Executors

object IrCatalog {
    fun load(context: Context): List<IrBrand> {
        val text = context.assets.open("ir_codes.json").bufferedReader().use { it.readText() }
        val brands = JSONObject(text).getJSONArray("brands")
        return List(brands.length()) { index ->
            val brand = brands.getJSONObject(index)
            val keys = brand.getJSONObject("keys")
            IrBrand(
                id = brand.getString("id"),
                name = brand.getString("name"),
                protocol = brand.getString("protocol"),
                device = brand.getInt("device"),
                subdevice = brand.getInt("subdevice"),
                keys = keys.keys().asSequence().associateWith(keys::getInt),
            )
        }
    }
}

object IrTransmitter {
    fun manager(context: Context): ConsumerIrManager? =
        (context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager)
            ?.takeIf { it.hasIrEmitter() }

    fun isAvailable(context: Context) = manager(context) != null
}

class IrClient(
    context: Context,
    private val brand: IrBrand,
    private val listener: TvClientListener,
) : TvClient {
    private val ir = IrTransmitter.manager(context)
    private val queue = Executors.newSingleThreadExecutor()
    private var toggle = false

    override val supportedKeys: Set<RemoteKey> =
        RemoteKey.entries.filter { it.name in brand.keys }.toSet()

    override fun connect() {
        listener.onState(
            if (ir != null) ConnectionState.Ready
            else ConnectionState.Failed("Este celular no tiene emisor infrarrojo."),
        )
    }

    override fun send(key: RemoteKey) {
        val function = brand.keys[key.name] ?: return
        val manager = ir ?: return
        if (queue.isShutdown) return
        toggle = !toggle
        val signal = IrEncoder.encode(brand, function, toggle)
        queue.execute { runCatching { manager.transmit(signal.carrierHz, signal.pattern) } }
    }

    override fun close() = queue.shutdown()
}
