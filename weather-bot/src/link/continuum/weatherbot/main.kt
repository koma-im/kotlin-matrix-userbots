package link.continuum.weatherbot

import com.github.kittinunf.result.Result
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import koma.Koma
import koma.controller.sync.MatrixSyncReceiver
import koma.matrix.MatrixApi
import koma.matrix.UserId
import koma.matrix.event.room_message.MRoomMessage
import koma.matrix.event.room_message.chat.TextMessage
import koma.matrix.room.naming.RoomId
import koma.storage.config.ConfigPaths
import koma.util.coroutine.adapter.retrofit.await
import koma.util.coroutine.adapter.retrofit.awaitMatrix
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import okhttp3.HttpUrl
import retrofit2.Response
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
    run(a.user, a.server, proxy, trust)
}

class MyArgs(parser: ArgParser) {
    val user by parser.storing("userid of the account to use")
    val server by parser.storing("url of the homeserver")
    val proxy by parser.storing("http proxy").default<String?>(null)
    val trust by parser.storing("path to additional certificate to trust")
            .default<String?>(null)
}

fun run(user: String, server: String, proxy: Proxy, trust: InputStream?) {
    val userId = UserId(user)
    val koma = Koma(ConfigPaths("."), proxy = proxy, addTrust = trust)
    val env = System.getenv()
    val token = env.get("TOKEN")
    if (token == null) {
        logger.error { "No TOKEN supplied" }
        return
    }
    val weather_token = env.get("WEATHER_TOKEN")
    if (weather_token == null) {
        logger.error { "No token supplied for weather api" }
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
    val weather = WeatherApi(koma.http.client, weather_token)
    try {
        val j = GlobalScope.launch { process(sync, userId.user, api, weather) }
        sync.startSyncing()
        runBlocking { j.join() }
    } finally {
        val nb = sync.since
        logger.debug { "Saving batch key $nb" }
        nb?.let { saveSyncBatchToken(nb) }
        runBlocking {
            sync.stopSyncing()
        }
    }
}

suspend fun process(sync: MatrixSyncReceiver, name: String, api: MatrixApi, weatherApi: WeatherApi) {
    for (s in sync.events) {
        if (s is Result.Success) {
            for (entry in s.value.rooms.join.entries) {
                val roomId = entry.key
                for (event in entry.value.timeline.events) {
                    if (event is MRoomMessage) {
                        if (name == event.content?.body?.substringBefore(' ')?.trimEnd(':')) {
                            GlobalScope.launch { respond(roomId, event, api, weatherApi) }
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

suspend fun respond(
        roomId: String,
        message: MRoomMessage,
        api:MatrixApi,
        weatherApi: WeatherApi
) {
    val text = message.content?.body?.substringAfter(' ')
    text ?: return
    if (!text.contains(keyword)) return
    val param = text.replace(keyword, "").trim()
    if (param.isBlank()) return
    logger.debug { "Got query for $param" }
    val res = weatherApi.weather(param).await()
    if (Instant.now().epochSecond - message.origin_server_ts > 30) {
        logger.warn { "It has been too long since the message " +
                "${message.content?.body?.take(20)} from ${message.sender} " +
                "was sent, not responding"
        }
        return
    }
    val m = formatReply(message.sender.user, param, res)
    val send = api.sendRoomMessage(RoomId(roomId), m).awaitMatrix()
    if (send is Result.Failure) {
        logger.warn { "Failed to send reply: ${send.error}" }
    }

}

fun formatReply(
        nick: String,
        city: String,
        result: Result<Response<CurrentWeather>, Exception>
): TextMessage {
    val text = when (result) {
        is Result.Failure -> {
            logger.warn { "Failed to get weather for $city: ${result.error}" }
            "$nick: Failed to get weather for $city: ${result.error.message?.take(20)}"
        }
        is Result.Success -> {
            val response = result.value
            val body = response.body()
            if (response.isSuccessful && body != null) {
                "$nick: ${weatherExcerpt(body)}"
            } else {
                "$nick: Weather service returned error for $city: ${response.errorBody()}"
            }
        }
    }
    return TextMessage(body = text)
}

fun weatherExcerpt(weather: CurrentWeather): String {
    val info =  "Weather for ${weather.name}: " +
            "${weather.weather.firstOrNull()?.description} " +
            "Temperature=${weather.main.temp}K " +
            "Humidity=${weather.main.humidity} " +
            "Cloudy=${weather.clouds.all}% " +
            "Wind=${weather.wind.speed}m/s, ${weather.wind.deg}°"
    return info
}
