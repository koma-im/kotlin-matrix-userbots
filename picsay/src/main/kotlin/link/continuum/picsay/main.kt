package link.continuum.picsay

import com.github.kittinunf.result.Result
import com.github.kittinunf.result.failure
import com.github.kittinunf.result.flatMap
import com.github.kittinunf.result.mapError
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
import koma.util.coroutine.adapter.retrofit.awaitMatrix
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import link.continuum.picsay.util.loadSyncBatchToken
import link.continuum.picsay.util.parseProxy
import link.continuum.picsay.util.partitionString
import link.continuum.picsay.util.saveSyncBatchToken
import link.continuum.text2img.unescapeUnicode
import mu.KotlinLogging
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import java.io.File
import java.io.InputStream
import java.net.Proxy

private val logger = KotlinLogging.logger {}

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
    run(a.user, a.server, proxy, trust, a.ipv4, a)
}

class MyArgs(parser: ArgParser) {
    val user by parser.storing("userId of the account to use")
    val server by parser.storing("url of the homeserver")
    val proxy by parser.storing("http proxy").default<String?>(null)
    val trust by parser.storing("path to additional certificate to trust")
            .default<String?>(null)
    val ipv4 by parser.flagging("use ipv4 only")
    val config by parser.storing("path to config file")
            .default<String>("config.json")
}

fun run(
        user: String,
        server: String,
        proxy: Proxy,
        trust: InputStream?,
        ipv4: Boolean = false,
        args: MyArgs
) {
    val t = loadTemplates(args.config).mapError {
        logger.error { "Load templates error" }
        it.printStackTrace()
        it
    }.flatMap { templates ->
        if (templates.isEmpty()) {
            logger.error { "Need at least one template, see " +
                    "https://github.com/koma-im/kotlin-matrix-userbots/tree/master/picsay " +
                    "for an example. "
            }
            Result.error(Exception("missing template"))
        } else {
            Result.of(templates)
        }
    }
    if (t is Result.Failure) return

    val userId = UserId(user)
    val k = Koma(
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
    val api = k.createApi(token, userId, url)
    val batch_key = loadSyncBatchToken()
    val sync = MatrixSyncReceiver(api, batch_key)
    Runtime.getRuntime().addShutdownHook(object : Thread(){
        override fun run() {
            val nb = sync.since
            logger.debug { "Saving batch key $nb" }
            nb?.let { saveSyncBatchToken(nb) }
        }
    })
    runBlocking {
        logger.info { "getting nick name of $user" }
        api.getDisplayName(user)
    }.fold({
        val p = Processor(k, api, t.get(), it.displayname)
        val j = GlobalScope.launch { processEvent(sync, p) }
        sync.startSyncing()
        logger.info { "Syncing started" }
        runBlocking { j.join() }
    }, {
        logger.error { "couldn't get nick name of $user" }
        it.printStackTrace()
    })

}


suspend fun processEvent(sync: MatrixSyncReceiver, processor: Processor) {
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
                for (event in entry.value.timeline.events) {
                    if (event is MRoomMessage) {
                        GlobalScope.launch {
                            processor.processEvent(roomId, event)
                        }
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
        private val api: MatrixApi,
        private val templates: Map<String, Template>,
        private val nick: String
) {
    init {
        require(templates.isNotEmpty(), { "need at least one template set up" } )
    }

    suspend fun processEvent(
            roomId: String,
            event: MRoomMessage) {
        val body = event.content?.body ?: return
        val (pre, txt) = partitionString(body, listOf(':', ' '))?:return
        if (pre == nick) {
            commands(txt, RoomId(roomId))
        } else {
            val template = templates.get(pre)
            template ?: return
            echoImage(roomId, api, txt, template)
        }
    }

    private suspend fun commands(txt: String, roomId: RoomId) {
        when (txt) {
            "leave" -> {
                api.sendMessage(roomId, TextMessage("Bye! I'm leaving the room as requested"))
                        .failure {
                            logger.error { "sending goodbye to $roomId, $it" }
                        }
                api.leavingRoom(roomId).awaitMatrix().failure {
                    logger.error { "leaving room $roomId, $it" }
                }
            }
            "help" -> {
                val keys = templates.keys.toList()
                var msg = "I'm like the program cowsay but with vivid pictures. " +
                        "Try sending a message prefixed with ${keys[0]} " +
                        "to see what I can do."
                if (templates.size > 1) {
                    msg += " You can also try any one of these prefixes: " +
                            "${keys.drop(1).joinToString(", ")}. "
                }
                msg += "If you want me to leave, please send \"$nick leave\""
                msg += "I'm written in Kotlin, visit $HOMEPAGE to get the code."
                api.sendMessage(roomId, TextMessage(msg))
                        .failure {
                            logger.error { "sending help to $roomId, $it" }
                        }
            }
            else -> {
                api.sendMessage(roomId,
                        TextMessage("unknown command $txt, you can try \"help\""))
                        .failure {
                            logger.error { "sending help $roomId, $it" }
                        }
            }
        }
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
        val k = templates.keys.toList()[0]
        val msg = "Hi, I'm a bot that generates pictures with messages. " +
                "Send a message like \"${k} hello\" " +
                "and see what happens! " +
                "If you want to send commands, mention me by my name $nick. " +
                "You can create instances of me without writing code. " +
                "See my home page $HOMEPAGE"
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

suspend fun echoImage(
        roomId: String,
        api: MatrixApi,
        rawText: String,
        template: Template
) {
    val txt = unescapeUnicode(rawText)
    val img = template.render(txt)
    if (img is Result.Failure) {
        val m = "can't generate image for text $txt, ${img.error}"
        logger.error(m)
        trySendMessage(api, roomId, m)
        return
    }
    val res = api.uploadByteArray(MediaType.get("image/png"), img.get()).awaitMatrix()
    if (res is Result.Failure) {
        val m = "can't upload generated image for text $txt, ${res.error}"
        logger.error(m)
        trySendMessage(api, roomId, m)
        return
    }
    val url = res.get()
    api.sendMessage(RoomId(roomId), ImageMessage("Generated using koma-im in Kotlin", url.content_uri)).failure {
        logger.error { "Sending image message $it" }
    }
}
