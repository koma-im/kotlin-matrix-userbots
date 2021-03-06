package link.continuum.collectivememory

import com.github.kittinunf.result.Result
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import koma.Koma
import koma.controller.sync.MatrixSyncReceiver
import koma.matrix.MatrixApi
import koma.matrix.UserId
import koma.matrix.event.room_message.MRoomMessage
import koma.network.client.okhttp.Dns
import koma.storage.config.ConfigPaths
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.io.InputStream
import java.net.Proxy
import java.time.Instant

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) = mainBody {
    val a = ArgParser(args).parseInto(::MyArgs)
    val po = a.proxy
    val proxy = if (po != null) {
        val pp = parseProxy(po)
        if (pp == null) {
            logger.error { "Invalid proxy: $po" }
            return@mainBody
        }
        pp
    } else {
        Proxy.NO_PROXY
    }
    val trust = a.trust?.let { path ->
        val f = File(path)
        try {
            f.inputStream()
        } catch (e: Exception) {
            logger.error { "Failed to load certificate file $path: $e" }
            null
        }
    }
    run(a.user, a.server, proxy, trust, a.ipv4)
}

class MyArgs(parser: ArgParser) {
    val user by parser.storing("userId of the account to use")
    val server by parser.storing("url of the homeserver")
    val proxy by parser.storing("http proxy").default<String?>(null)
    val trust by parser.storing("path to additional certificate to trust")
            .default<String?>(null)
    val ipv4 by parser.flagging("use ipv4 only")
}

fun run(
        user: String,
        server: String,
        proxy: Proxy,
        trust: InputStream?,
        ipv4: Boolean = false
) {
    val userId = UserId(user)
    val koma = Koma(
            ConfigPaths("."),
            proxy = proxy,
            http_builder = if (ipv4) OkHttpClient.Builder().dns(Dns.onlyV4) else null,
            addTrust = trust
    )
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
    Runtime.getRuntime().addShutdownHook(object : Thread(){
        override fun run() {
            val nb = sync.since
            logger.debug { "Saving batch key $nb" }
            nb?.let { saveSyncBatchToken(nb) }
        }
    })
    val j = GlobalScope.launch { process(sync, userId.user, api) }
    sync.startSyncing()
    logger.info { "Syncing started" }
    runBlocking { j.join() }
}

suspend fun process(sync: MatrixSyncReceiver, name: String, api: MatrixApi) {
    for (s in sync.events) {
        if (s is Result.Success) {
            val rooms = s.value.rooms
            logger.trace { "Received update for ${rooms.join.size} joined rooms " +
                    "${rooms.invite.size} invited rooms ${rooms.leave.size} left rooms" }
            for (entry in rooms.join.entries) {
                val roomId = entry.key
                for (event in entry.value.timeline.events) {
                    if (event is MRoomMessage) {
                        GlobalScope.launch { respond(roomId, event, api) }
                        if (name == event.content?.body?.substringBefore(' ')?.trimEnd(':')) {
                            // mention
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

suspend fun respond(
        roomId: String,
        message: MRoomMessage,
        api:MatrixApi
) {
    val text = message.content?.body?.substringAfter(' ')
    text ?: return

    if (Instant.now().epochSecond - message.origin_server_ts > 30) {
        logger.warn { "It has been too long since the message " +
                "${message.content?.body?.take(20)} from ${message.sender} " +
                "was sent, not responding"
        }
        return
    }

}
