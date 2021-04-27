package io.doodlebot.backend.service

import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import java.io.FileInputStream
import java.time.format.DateTimeFormatter
import java.util.*
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
    val authDate: String,
    val firstName: String? = null,
    val id: String,
    val lastName: String? = null,
    val photoUrl: String? = null,
    val username: String,
    val hash: String
) {
    init {
        // Validate input
        val usernameRegex = "[a-zA-Z0-9_]{5,32}".toRegex()
        check(usernameRegex.matches(username))
        val authDateRegex = "[0-9]{10}".toRegex()
        check(authDateRegex.matches(authDate))
        // Verify Telegram data
        val dataCheckString = listOfNotNull(
            "auth_date=${authDate}",
            if (firstName != null) "first_name=${firstName}" else null,
            "id=${id}",
            if (lastName != null) "last_name=${lastName}" else null,
            if (photoUrl != null) "photo_url=${photoUrl}" else null,
            "username=${username}"
        ).joinToString("\n")
        println(dataCheckString)
        println(hash)
        println(HashUtil.createHash(dataCheckString))
        assert(hash == HashUtil.createHash(dataCheckString))
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

object DateUtil {
    val inputFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
}

data class JsonData(val dates: List<String>, val title: String)

