package com.folio.wealth.data

import android.content.Context
import android.util.AtomicFile
import com.folio.wealth.model.PortfolioData
import java.io.File

interface PortfolioStore {
    fun load(): PortfolioData
    fun save(data: PortfolioData)
}

/** Atomic writes keep the previous file intact when a save cannot finish. */
class LocalPortfolioStore(baseFile: File) : PortfolioStore {
    constructor(context: Context) : this(File(context.applicationContext.filesDir, "portfolio-v2.dat"))
    private val file = AtomicFile(baseFile)

    override fun load(): PortfolioData {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return PortfolioData()
        require(file.baseFile.length() <= 8 * 1024 * 1024) { "资产文件过大" }
        return PortfolioCodec.decode(file.readFully())
    }

    override fun save(data: PortfolioData) {
        val bytes = PortfolioCodec.encode(data)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}
