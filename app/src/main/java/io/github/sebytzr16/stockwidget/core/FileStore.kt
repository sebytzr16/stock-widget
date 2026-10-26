package io.github.sebytzr16.stockwidget.core

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Two small JSON files: what the user owns (user.json) and the last market data
 * (market.json). Each is read once and rewritten atomically, so a crash or a full disk
 * never leaves a half-written file behind.
 */
class FileStore(private val dir: File) {

    private val userFile = File(dir, "user.json")
    private val marketFile = File(dir, "market.json")

    fun loadUser(): UserData = read(userFile)?.let { runCatching { Codec.decodeUser(it) }.getOrNull() } ?: UserData()

    fun loadMarket(): MarketData =
        read(marketFile)?.let { runCatching { Codec.decodeMarket(it) }.getOrNull() } ?: MarketData()

    fun saveUser(u: UserData) = write(userFile, Codec.encodeUser(u))

    fun saveMarket(m: MarketData) = write(marketFile, Codec.encodeMarket(m))

    private fun read(f: File): String? = try {
        if (f.exists()) f.readText() else null
    } catch (e: IOException) {
        null
    }

    @Synchronized
    private fun write(target: File, text: String) {
        try {
            dir.mkdirs()
            val tmp = File(dir, target.name + ".tmp")
            FileOutputStream(tmp).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        } catch (e: IOException) {
            // Keep running on the in-memory copy; the next change retries the write.
        }
    }
}
