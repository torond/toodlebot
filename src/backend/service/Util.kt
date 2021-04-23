package io.doodlebot.backend.service

import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import java.io.FileInputStream
import java.util.Properties
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HashUtil {
    private val sha256Hmac = Mac.getInstance("HmacSHA256")

    init {
        sha256Hmac.init(SecretKeySpec(DigestUtils.sha256(Env.botToken), "HmacSHA256"))
    }

    fun createHash(data: String): String {
        return Hex.encodeHexString(sha256Hmac.doFinal(data.toByteArray()))
    }
}

data class LoginData(
    val auth_date: String,
    val first_name: String? = null,
    val id: String,
    val last_name: String? = null,
    val photo_url: String? = null,
    val username: String,
    val hash: String
) {
    init {
        // Verify Telegram data
        val dataCheckString = listOfNotNull(
            "auth_date=${auth_date}",
            if (first_name != null) "first_name=${first_name}" else null,
            "id=${id}",
            if (last_name != null) "last_name=${last_name}" else null,
            if (photo_url != null) "photo_url=${photo_url}" else null,
            "username=${username}"
        ).joinToString("\n")
        //assert(hash == HashUtil.createHash(dataCheckString))
    }
}

object Env {
    // Holds data from environment.properties
    private val props = Properties()
    private val inputStream = FileInputStream("environment.properties")
    val host: String
    val port: String
    val botToken: String
    val botUsername: String

    init {
        props.load(inputStream)
        host = props.getProperty("host")
        port = props.getProperty("port")
        botToken = props.getProperty("bot_token")
        botUsername = props.getProperty("bot_username")
    }
}

data class LoginSession(val username: String, val auth_date: String)

