package link.continuum.avecho

import com.github.kittinunf.result.Result
import com.github.kittinunf.result.failure
import com.github.kittinunf.result.flatMap
import com.github.kittinunf.result.map
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import koma.Koma
import koma.controller.sync.MatrixSyncReceiver
import koma.matrix.MatrixApi
import koma.matrix.UserId
import koma.matrix.event.room_message.MRoomMessage
import koma.matrix.event.room_message.chat.ImageMessage
import koma.matrix.event.room_message.chat.TextMessage
import koma.matrix.room.naming.RoomId
import koma.network.client.okhttp.Dns
import koma.storage.config.ConfigPaths
import koma.util.coroutine.adapter.okhttp.await
import koma.util.coroutine.adapter.okhttp.extract
import koma.util.coroutine.adapter.retrofit.awaitMatrix
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import link.continuum.text2img.TextRenderer
import link.continuum.text2img.loadImageBytes
import link.continuum.text2img.unescapeUnicode
import mu.KotlinLogging
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import java.net.Proxy

private val logger = KotlinLogging.logger {}

const val CALLNAME: String = "avecho"
const val HOMEPAGE = "https://github.com/koma-im"

fun main(args: Array<String>) {
    runMain(args)
}

fun runMain(args: Array<String>) = mainBody {
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
    val p = Processor(koma, api)
    val j = GlobalScope.launch { processEvent(sync, p) }
    sync.startSyncing()
    logger.info { "Syncing started" }
    runBlocking { j.join() }
}


suspend fun processEvent(sync: MatrixSyncReceiver, processor: Processor) {
    val lastEventTime = HashMap<String, Long>()
    for (s in sync.events) {
        if (s is Result.Success) {
            val rooms = s.value.rooms
            logger.trace { "Received update for ${rooms.join.size} joined rooms " +
                    "${rooms.invite.size} invited rooms ${rooms.leave.size} left rooms" }
            for (entry in rooms.invite) {
                val rid = entry.key
                val state = entry.value
                logger.info { "Invited to $rid, event $state" }
                GlobalScope.launch { processor.joinGreet(rid) }
            }
            for (entry in rooms.join.entries) {
                val roomId = entry.key
                val allEvents = entry.value.timeline.events
                val t = lastEventTime.getOrDefault(roomId, 0)
                val newEvents  = allEvents.filter { it.origin_server_ts > t }
                val ignored = allEvents.size - newEvents.size
                if (ignored > 0 ) {
                    logger.info { "ignored ${ignored} old events" }
                }
                val newt = newEvents.lastOrNull()?.origin_server_ts
                if (newt != null) lastEventTime.put(roomId, t)
                for (event in newEvents) {
                    if (event is MRoomMessage) {
                       processor.processEvent(roomId, event)
                    }
                }
            }
        } else if (s is Result.Failure) {
            logger.warn { "sync failure ${s.error}" }
            delay(6 * 1000)
            logger.warn { "retrying sync" }
            sync.startSyncing()
        }
    }
}

class Processor(
        private val km: Koma,
        private val api: MatrixApi
) {
    private val renderer = TextRenderer()
    suspend fun processEvent(
            roomId: String,
            event: MRoomMessage) {
        val msgTxt = event.content?.body ?: return
        if (CALLNAME != msgTxt.substringBefore(' ')) return
        val txt = msgTxt.substringAfter(' ')
        GlobalScope.launch {
            val rid = RoomId(roomId)
            if (!commands(txt, rid)) {
                val p = EchoProcessor(km, api, roomId, event.sender, txt, renderer)
                p.process()
            }
        }
    }

    suspend fun commands(txt: String, roomId: RoomId): Boolean {
        when (txt) {
            "leave" -> {
                api.sendMessage(roomId, TextMessage("Bye! I'm leaving the room as requested"))
                        .failure {
                            logger.error { "sending goodbye to $roomId, $it" }
                        }
                api.leavingRoom(roomId).awaitMatrix().failure {
                    logger.error { "leaving room $roomId, $it" }
                }
                return true
            }
            "help" -> {
                val msg = "I'm a bot for drawing text and avatar onto image. " +
                        "To use me, prefix message with $CALLNAME. " +
                        "For example: avecho hello. And \"hello\" and your avatar" +
                        "will appear in an image. " +
                        "I'm written in Kotlin, visit $HOMEPAGE to get the code."
                api.sendMessage(roomId, TextMessage(msg))
                        .failure {
                            logger.error { "sending help to $roomId, $it" }
                        }
                return true
            }
        }
        return false
    }

    suspend fun joinGreet(roomId: String) {
        val joinResult = api.joinRoom(roomid = RoomId(roomId)).awaitMatrix()
        when (joinResult) {
            is Result.Failure -> {
                logger.error { "can't join $roomId, error ${joinResult.error}" }
                return
            }
            is Result.Success -> {
                logger.info { "joined room ${joinResult.value.room_id}" }
            }
        }
        val msg = "Hi, I'm a bot for drawing texts. " +
                "Send a message like $CALLNAME TEXT, " +
                "and I'll draw TEXT as image, and include your avatar. " +
                "Send avecho help for more info. " +
                "My code (in Kotlin) is at $HOMEPAGE"
        api.sendMessage(roomId = RoomId(roomId),
                message = TextMessage(msg))
                .failure {
                    logger.error { "sending greetings, $it" }
        }
    }
}

suspend fun trySendMessage(api: MatrixApi, roomId: String, text: String) {
    api.sendMessage(RoomId(roomId), TextMessage(text))
            .failure {
                logger.error { "sending message $text to $roomId, $it" }
            }
}

class EchoProcessor(
        private val k: Koma,
        private val api: MatrixApi,
        private val roomId: String,
        private val sender: UserId,
        private val rawText: String,
        private val renderer: TextRenderer
) {
    suspend fun process() {
        val av = api.service.getAvatar(sender).awaitMatrix()
        if (av is Result.Failure) {
            val m = "Found no avatar of ${sender}, ${av.error.message}. " +
                    "Please set an avatar on matrix.org. Or fork this bot to add new " +
                    "ways to get avatars. Source: $HOMEPAGE"
            logger.error(m)
            trySendMessage(api, roomId, m)
            return
        }
        val aurl = av.get().avatar_url
        if (aurl == null) {
            val m = "${sender} has not set an avatar yet. " +
                    "Please set an avatar on matrix.org. Or fork this bot to add new " +
                    "ways to get avatars. Source: $HOMEPAGE"
            logger.error(m)
            trySendMessage(api, roomId, m)
            return
        }
        val murl = api.getMediaUrl(aurl)
        if (murl is Result.Failure) {
            val m = "invalid avatar url $murl"
            logger.error(m)
            trySendMessage(api, roomId, m)
            return
        }
        val u = murl.get()
        val req = Request.Builder().url(u).build()
        val ares = k.http.client.newCall(req).await()
                .flatMap { it.extract() }.map { it.bytes() }
        if (ares is Result.Failure) {
            val m = "can't fetch avatar at $u, ${ares.error}"
            logger.error(m)
            trySendMessage(api, roomId, m)
            return
        }
        val abs = ares.get()
        val im = loadImageBytes(abs)
        if (im is Result.Failure) {
            val m = "can't parse image ${im.error}"
            logger.error(m)
            trySendMessage(api, roomId, m)
            return
        }
        echoImage(roomId, im.get(), api)
    }

    suspend fun echoImage(
            roomId: String,
            image: BufferedImage,
            api: MatrixApi
    ) {
        val img = renderer.generateImage(unescapeUnicode(rawText), image)
        val res = api.uploadByteArray(MediaType.get("image/png"), img).awaitMatrix()
        if (res is Result.Failure) {
            val m = "can't upload generated image for $sender, ${res.error}"
            logger.error(m)
            trySendMessage(api, roomId, m)
            return
        }
        val url = res.get()
        val alt = "$sender: $rawText"
        api.sendMessage(RoomId(roomId), ImageMessage(alt, url.content_uri)).failure {
            logger.error { "Sending image message $it" }
        }
    }
}
