package com.moonliter.app.data

import com.moonliter.app.BuildConfig
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
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
        try {
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

            val responseCode = connection.responseCode
            val responseText = try {
                val stream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            } finally {
                connection.disconnect()
            }

            val responseJson = parseJson(responseText)
            if (!responseJson.optBoolean("success", false)) {
                throw ApiException(responseJson.optString("message", "요청을 처리하지 못했습니다."))
            }

            parse(responseJson.optJSONObject("data") ?: throw ApiException("서버 응답 형식이 올바르지 않습니다."))
        } catch (exception: ApiException) {
            throw exception
        } catch (exception: IOException) {
            throw ApiException("서버에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.")
        } catch (exception: JSONException) {
            throw ApiException("서버 응답을 읽지 못했습니다. 잠시 후 다시 시도해주세요.")
        }
    }

    private fun parseJson(responseText: String): JSONObject {
        if (responseText.isBlank()) {
            throw ApiException("서버 응답이 비어 있습니다. 잠시 후 다시 시도해주세요.")
        }

        return JSONObject(responseText)
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

class ApiException(message: String) : RuntimeException(message)
