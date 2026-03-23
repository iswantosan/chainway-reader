package com.stok.middleware.data.model

enum class StockOpMode(val apiValue: String?, val label: String) {
    MASUK(apiValue = "masuk", label = "MASUK"),
    KELUAR(apiValue = "keluar", label = "KELUAR"),
    OPNAME(apiValue = "opname", label = "OPNAME"),
    READONLY(apiValue = null, label = "READONLY")
}

