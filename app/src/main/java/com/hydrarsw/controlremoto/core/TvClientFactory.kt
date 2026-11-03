package com.hydrarsw.controlremoto.core

import android.content.Context
import com.hydrarsw.controlremoto.androidtv.AndroidTvClient
import com.hydrarsw.controlremoto.ir.IrCatalog
import com.hydrarsw.controlremoto.ir.IrClient
import com.hydrarsw.controlremoto.lg.WebOsClient
import com.hydrarsw.controlremoto.samsung.SamsungClient

fun createClient(context: Context, tv: SavedTv, listener: TvClientListener): TvClient = when (tv.kind) {
    TvKind.ANDROID_TV -> AndroidTvClient(tv, listener)
    TvKind.SAMSUNG -> SamsungClient(tv, listener)
    TvKind.LG -> WebOsClient(tv, listener)
    TvKind.INFRARED -> IrClient(
        context,
        IrCatalog.load(context).first { it.id == tv.irBrand },
        listener,
    )
}
