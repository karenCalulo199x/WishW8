package com.karencalulo.wishwait.data.cloud

import com.karencalulo.wishwait.domain.model.DriveFile
import com.karencalulo.wishwait.domain.repository.CloudBackupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

/**
 * Backup file in the hidden app folder ("appDataFolder") of the person's own Google Drive,
 * through the Drive REST API. Small JSON files only, so plain HttpURLConnection is enough.
 */
class DriveCloudBackupRepository(private val auth: DriveAuth) : CloudBackupRepository {

    override suspend fun upload(content: String): Boolean = withContext(Dispatchers.IO) {
        val token = auth.silentToken() ?: return@withContext false
        runCatching {
            val old = listFiles(token) ?: return@runCatching false
            // Create the new file first, then remove older copies: a backup is never lost mid-way.
            if (!create(token, content)) return@runCatching false
            old.forEach { deleteFile(token, it.first) }
            true
        }.getOrDefault(false)
    }

    override suspend fun download(): DriveFile = withContext(Dispatchers.IO) {
        val token = auth.silentToken() ?: return@withContext DriveFile.Unreachable
        runCatching {
            val files = listFiles(token) ?: return@runCatching DriveFile.Unreachable
            val (id, modified) = files.maxByOrNull { it.second } ?: return@runCatching DriveFile.NotFound
            val body = request(token, "GET", "$API/files/$id?alt=media") ?: return@runCatching DriveFile.Unreachable
            DriveFile.Found(modified, body)
        }.getOrDefault(DriveFile.Unreachable)
    }

    override suspend fun delete(): Boolean = withContext(Dispatchers.IO) {
        val token = auth.silentToken() ?: return@withContext false
        runCatching {
            val files = listFiles(token) ?: return@runCatching false
            files.all { deleteFile(token, it.first) }
        }.getOrDefault(false)
    }

    /** (id, modified time) of every backup file, or null when Drive can't be reached. */
    private fun listFiles(token: String): List<Pair<String, Long>>? {
        val q = URLEncoder.encode("name = '$FILE_NAME' and trashed = false", "UTF-8")
        val fields = URLEncoder.encode("files(id,modifiedTime)", "UTF-8")
        val body = request(token, "GET", "$API/files?spaces=appDataFolder&q=$q&fields=$fields&pageSize=10") ?: return null
        val files = JSONObject(body).optJSONArray("files") ?: return emptyList()
        return (0 until files.length()).map { i ->
            val f = files.getJSONObject(i)
            f.getString("id") to runCatching { Instant.parse(f.getString("modifiedTime")).toEpochMilli() }.getOrDefault(0L)
        }
    }

    private fun create(token: String, content: String): Boolean {
        val boundary = "wishwait-${System.nanoTime()}"
        val metadata = JSONObject()
            .put("name", FILE_NAME)
            .put("parents", org.json.JSONArray().put("appDataFolder"))
            .put("mimeType", "application/json")
        val body = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString()).append("\r\n")
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(content).append("\r\n")
            append("--$boundary--\r\n")
        }
        return request(
            token,
            "POST",
            "$UPLOAD_API/files?uploadType=multipart&fields=id",
            body = body,
            contentType = "multipart/related; boundary=$boundary",
        ) != null
    }

    private fun deleteFile(token: String, id: String): Boolean = request(token, "DELETE", "$API/files/$id") != null

    /** Response body on success (2xx), null otherwise. */
    private fun request(token: String, method: String, url: String, body: String? = null, contentType: String? = null): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = method
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType ?: "application/json; charset=UTF-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val FILE_NAME = "wishwait-backup.json"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"
        private const val TIMEOUT_MS = 15_000
    }
}
