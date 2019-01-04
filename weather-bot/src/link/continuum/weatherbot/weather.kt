package link.continuum.weatherbot

import com.github.kittinunf.result.Result
import com.squareup.moshi.Json
import koma.matrix.json.MoshiInstance
import koma.util.coroutine.adapter.retrofit.await
import mu.KotlinLogging
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

private val logger = KotlinLogging.logger {}

class WeatherApi(client: OkHttpClient, private val token: String) {
    private val openWeatherMap: OpenWeatherMapApi

    suspend fun weather(city: String): Result<CurrentWeather, Exception> {
        val res = openWeatherMap.weather(city, token).await()
        when (res) {
            is Result.Success -> {
                val response = res.value
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    return Result.of(body)
                } else {
                    val es = response.errorBody()?.source()?.readUtf8()
                    val we = es?.let { WeatherErrorJson.parse(es) }
                    if (we != null) return Result.error(we)
                    return Result.error(Exception(
                            "Weather api error ${response.code()}, ${response.message()}, $es"))
                }
            }
            is Result.Failure -> return Result.error(res.error)
        }
    }

    init {
        val moshi = MoshiInstance.moshi
        val url = HttpUrl.Builder().scheme("https")
                .host("api.openweathermap.org")
                .addPathSegments("data/2.5/")
                .build()
        openWeatherMap = Retrofit.Builder()
                .baseUrl(url)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .client(client).build()
                .create(OpenWeatherMapApi::class.java)
    }
}

private interface OpenWeatherMapApi {
    @GET("weather")
    fun weather(@Query("q") city: String,
                @Query("appid") token: String
    ): Call<CurrentWeather>
}

private object WeatherErrorJson {
    private val jsonAdapter = MoshiInstance.moshi.adapter(WeatherError::class.java)
    fun parse(text: String): WeatherError? {
        try {
            return jsonAdapter.fromJson(text)
        } catch (e: Exception) {
            logger.error { "Failed to parse error response $text. Error $e" }
            return null
        }
    }
}

data class WeatherError(
        val cod: String,
        override val message: String
): Exception("Weather api error $cod: $message")

/**
 * response from openweathermap
 */
data class CurrentWeather(
        val coord: Coordinate,
        val weather: List<WeatherItem>,
        val main: MainWeather,
        val wind: Wind,
        val clouds: Clouds,
        val dt: Long, // time of data calculation
        val sys: Sys,
        val id: Int, // of city
        val name: String, // of city
        val rain: Rain
)

data class Coordinate(
        val lon: Float,
        val lat: Float
)

data class WeatherItem(
        val id: Int,
        val main: String,
        val description: String,
        val icon: String
)

data class MainWeather(
        val temp: Float,
        val pressure: Float,
        /**
         * actually humidity seems to be integer
         */
        val humidity: Float,
        val temp_min: Float,
        val temp_max: Float,
        val sea_level: Float,
        val grnd_level: Float
)

data class Wind(
        val speed: Float,
        val deg: Float // meteorological
)

data class Clouds(
        /**
         * Cloudiness, %
         * actually seems to use integer
          */
        val all: Float
)

data class Sys(
        val country: String,
        val sunrise: Long, // time uses UTC
        val sunset: Long
)

data class Rain(
        @Json(name = "3h")
        val h3: Float?,
        @Json(name = "1h")
        val h1: Float?
)
