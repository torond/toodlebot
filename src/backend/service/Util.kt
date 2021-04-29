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

    fun sha256hmac(data: String): String {
        return Hex.encodeHexString(sha256Hmac.doFinal(data.toByteArray()))
    }

    fun sha256(data: String?): String? {
        if (data != null) {
            return Hex.encodeHexString(DigestUtils.sha256(data))
        }
        return null
    }
}

/**
 * Note: Do not use .copy as it ignores the data validation (https://youtrack.jetbrains.com/issue/KT-11914).
 */
data class LoginData private constructor(
        val authDate: String,
        val firstName: String? = null,
        val userId: String,
        val lastName: String? = null,
        val photoUrl: String? = null,
        var username: String? = null,
        val hash: String
) {
    companion object {
        operator fun invoke(
                authDate: String,
                firstName: String?,
                userId: String,
                lastName: String?,
                photoUrl: String?,
                username: String?,
                hash: String
        ): LoginData {
            // Validate input
            /*val usernameRegex = "[a-zA-Z0-9_]{5,32}".toRegex()
            check(usernameRegex.matches(username))*/
            val authDateRegex = "[0-9]{10}".toRegex()
            check(authDateRegex.matches(authDate))
            // Verify Telegram data
            val dataCheckString = listOfNotNull(
                    "auth_date=${authDate}",
                    if (firstName != null) "first_name=${firstName}" else null,
                    "id=${userId}",
                    if (lastName != null) "last_name=${lastName}" else null,
                    if (photoUrl != null) "photo_url=${photoUrl}" else null,
                    "username=${username}"
            ).joinToString("\n")
            assert(hash == HashUtil.sha256hmac(dataCheckString))
            return LoginData(authDate,
                    HashUtil.sha256(firstName),  // Hash private data
                    userId,
                    HashUtil.sha256(lastName),
                    photoUrl,
                    HashUtil.sha256(username),
                    hash)
        }
    }
}

/**
 * Holds the [userId]
 */
data class LoginSession(val userId: String)

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

