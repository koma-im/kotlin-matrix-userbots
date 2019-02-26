package link.continuum.picsay.util

import mu.KotlinLogging
import java.io.File
import java.io.IOException

private val logger = KotlinLogging.logger {}

fun saveSyncBatchToken(next_batch: String) {
    val syncTokenFile = File("next_batch")
    try {
        syncTokenFile.writeText(next_batch)
    } catch (e: IOException) {
        logger.warn {
            "Failed to save sync pagination to ${syncTokenFile.path}: $e"
        }
        return
    }
}

fun loadSyncBatchToken(): String? {
    val syncTokenFile = File("next_batch")
    try {
        val batch = syncTokenFile.readText()
        syncTokenFile.delete()
        return batch
    } catch (e: IOException) {
        logger.warn {
            "Failed to load sync pagination from ${syncTokenFile.path}: $e"
        }
        return null
    }
}
