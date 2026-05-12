package com.moonliter.app.data

import com.moonliter.app.BuildConfig
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PostcardApiClient(
    private val baseUrl: String = BuildConfig.API_BASE_URL
) {

    suspend fun createPostcard(userId: Long, content: String): PostcardResponse =
        request(
            path = "/api/v1/postcards",
            method = "POST",
            userId = userId,
            body = JSONObject().put("content", content)
        ) { data ->
            PostcardResponse(
                id = data.getLong("id"),
                content = data.getString("content"),
                status = data.getString("status"),
                createdAt = data.getString("createdAt")
            )
        }

    suspend fun assignTodayPostcard(userId: Long): TodayPostcardResponse =
        request(
            path = "/api/v1/postcards/today/delivery",
            method = "POST",
            userId = userId
        ) { data ->
            data.toTodayPostcard()
        }

    suspend fun getTodayPostcard(userId: Long): TodayPostcardResponse =
        request(
            path = "/api/v1/postcards/today",
            method = "GET",
            userId = userId
        ) { data ->
            data.toTodayPostcard()
        }

    private suspend fun <T> request(
        path: String,
        method: String,
        userId: Long,
        body: JSONObject? = null,
        parse: (JSONObject) -> T
    ): T = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-User-Id", userId.toString())
            doInput = true
        }

        if (body != null) {
            connection.doOutput = true
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body.toString())
            }
        }

        val responseText = try {
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }

        val responseJson = JSONObject(responseText)
        if (!responseJson.optBoolean("success", false)) {
            throw IllegalStateException(responseJson.optString("message", "요청을 처리하지 못했습니다."))
        }

        parse(responseJson.getJSONObject("data"))
    }

    private fun JSONObject.toTodayPostcard(): TodayPostcardResponse =
        TodayPostcardResponse(
            deliveryId = getLong("deliveryId"),
            postcardId = getLong("postcardId"),
            content = getString("content"),
            deliveredDate = getString("deliveredDate"),
            deliveredAt = getString("deliveredAt")
        )
}

data class PostcardResponse(
    val id: Long,
    val content: String,
    val status: String,
    val createdAt: String
)

data class TodayPostcardResponse(
    val deliveryId: Long,
    val postcardId: Long,
    val content: String,
    val deliveredDate: String,
    val deliveredAt: String
)
