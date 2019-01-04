package link.continuum.weatherbot

import com.github.kittinunf.result.Result
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import koma.Koma
import koma.controller.sync.MatrixSyncReceiver
import koma.matrix.MatrixApi
import koma.matrix.UserId
import koma.matrix.event.room_message.MRoomMessage
import koma.storage.config.ConfigPaths
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import okhttp3.HttpUrl
import java.net.Proxy

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) = mainBody {
    val a = ArgParser(args).parseInto(::MyArgs)
    run(a.user, a.server)
}

class MyArgs(parser: ArgParser) {
    val user by parser.storing("userid of the account to use")
    val server by parser.storing("url of the homeserver")
}

fun run(user: String, server: String) {
    val userId = UserId(user)
    val koma = Koma(ConfigPaths("."), proxy = Proxy.NO_PROXY)
    val env = System.getenv()
    val token = env.get("TOKEN")
    if (token == null) {
        logger.error { "No TOKEN supplied" }
        return
    }
    val url = HttpUrl.parse(server)
    if (url == null) {
        logger.error { "Invalid homeserver url $server" }
        return
    }
    val api = koma.createApi(token, userId, url)
    val batch_key = loadSyncBatchToken()
    val sync = MatrixSyncReceiver(api, batch_key)
    try {
        GlobalScope.launch { process(sync, userId.user, api) }
        sync.startSyncing()
    } finally {
        val nb = sync.since
        logger.debug { "Saving batch key $nb" }
        nb?.let { saveSyncBatchToken(nb) }
        runBlocking {
            sync.stopSyncing()
        }
    }
}

suspend fun process(sync: MatrixSyncReceiver, name: String, api: MatrixApi) {
    for (s in sync.events) {
        if (s is Result.Success) {
            for (entry in s.value.rooms.join.entries) {
                val roomId = entry.key
                for (event in entry.value.timeline.events) {
                    if (event is MRoomMessage) {
                        if (name == event.content?.body?.substringBefore(' ')?.trimEnd(':')) {
                            GlobalScope.launch { respond(roomId, event, api) }
                        }
                    }
                }
            }
        } else if (s is Result.Failure) {
            logger.warn { "sync failure ${s.error}" }
            delay(60 * 1000)
            logger.warn { "retrying sync" }
            sync.startSyncing()
        }
    }
}

const val keyword = "weather"

suspend fun respond(roomId: String, message: MRoomMessage, api:MatrixApi) {
    val text = message.content?.body?.substringAfter(' ')
    text ?: return
    if (!text.contains(keyword)) return
    val param = text.replace(keyword, "").trim()
    if (param.isBlank()) return
    logger.debug { "Got query for $param" }
}
