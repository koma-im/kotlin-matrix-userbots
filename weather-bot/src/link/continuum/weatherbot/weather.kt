 package link.continuum.weatherbot

import com.squareup.moshi.Json
import koma.matrix.json.MoshiInstance
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

class WeatherApi(client: OkHttpClient, private val token: String) {
    private val openWeatherMap: OpenWeatherMapApi

    fun weather(city: String) = openWeatherMap.weather(city, token)

    init {
        val moshi = MoshiInstance.moshi
        val url = HttpUrl.Builder().scheme("https")
                .host("api.openweathermap.org")
                .addPathSegment("data")
                .addPathSegment("2.5")
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
