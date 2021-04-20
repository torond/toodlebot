package io.doodlebot.backend.service

import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import java.io.FileInputStream
import java.util.Properties
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class JsonData(val dates: List<String>, val meta: Map<String, String>)

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
    val auth_date: String? = null,
    val first_name: String? = null,
    val id: String? = null,
    val last_name: String? = null,
    val photo_url: String? = null,
    val username: String? = null,
    val hash: String? = null
) {
    init {
        // Verify Telegram data
        val dataCheckString = listOfNotNull(
            if (auth_date != null) "auth_date=${auth_date}" else null,
            if (first_name != null) "first_name=${first_name}" else null,
            if (id != null) "id=${id}" else null,
            if (last_name != null) "last_name=${last_name}" else null,
            if (photo_url != null) "photo_url=${photo_url}" else null,
            if (username != null) "username=${username}" else null
        ).joinToString("\n")
        assert(hash == HashUtil.createHash(dataCheckString))
    }
}

object Env {
    // Holds data from environment.properties
    private val props = Properties()
    private val inputStream = FileInputStream("environment.properties")
    val localIp: String
    val botName: String
    val botToken: String

    init {
        props.load(inputStream)
        localIp = props.getProperty("local_ip")
        botName = props.getProperty("bot_name")
        botToken = props.getProperty("bot_token")
    }
}

