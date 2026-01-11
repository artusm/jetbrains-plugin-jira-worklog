package com.github.artusm.jetbrainspluginjiraworklog.jira

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

interface HttpClient {
    fun executeGet(url: String, token: String): String
    fun executePost(url: String, token: String, requestBody: String): String
}

class DefaultHttpClient : HttpClient {
    override fun executeGet(url: String, token: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                return connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw IOException("HTTP $responseCode: $errorStream")
            }
        } finally {
            connection.disconnect()
        }
    }

    override fun executePost(url: String, token: String, requestBody: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            // Write request body
            connection.outputStream.use { os ->
                val input = requestBody.toByteArray(StandardCharsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK) {
                return connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw IOException("HTTP $responseCode: $errorStream")
            }
        } finally {
            connection.disconnect()
        }
    }
}
