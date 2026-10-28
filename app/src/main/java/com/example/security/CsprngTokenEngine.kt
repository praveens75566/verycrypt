package com.example.security

import java.security.SecureRandom
import kotlin.math.ln

data class GeneratedTokenResult(
    val token: String,
    val tokenType: String,
    val entropyBits: Double,
    val shannonEntropyPerByte: Double,
    val strengthRating: String,
    val bruteForceEstimate: String
)

object CsprngTokenEngine {

    private val secureRandom = SecureRandom()
    private val LOG_2 = ln(2.0)

    private val UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ" // Omitted lookalike 'I', 'O'
    private val LOWERCASE = "abcdefghijkmnopqrstuvwxyz" // Omitted lookalike 'l'
    private val DIGITS = "23456789" // Omitted '0', '1'
    private val SYMBOLS = "!@#$%^&*()-_=+[]{}<>?"

    private val DICEWARE_WORDLIST = listOf(
        "abacus", "abbey", "amber", "anchor", "anthem", "apex", "apron", "arch", "armor", "arrow",
        "atlas", "atom", "aurora", "beacon", "breeze", "bridge", "bronze", "cabin", "canyon", "castle",
        "cedar", "cipher", "citadel", "clover", "cobalt", "comet", "compass", "copper", "coral", "crater",
        "crystal", "dawn", "delta", "drift", "dune", "eagle", "echo", "eclipse", "ember", "falcon",
        "feather", "flint", "forest", "fossil", "frost", "galaxy", "garnet", "glacier", "granite", "grove",
        "haven", "hawk", "horizon", "island", "jasper", "jungle", "lagoon", "lantern", "lapis", "ledge",
        "legacy", "lynx", "marble", "matrix", "meadow", "meteor", "mirage", "monolith", "nebula", "nexus",
        "oasis", "obsidian", "ocean", "omega", "onyx", "orbit", "peak", "pebble", "phoenix", "pillar",
        "pinnacle", "plateau", "polar", "prism", "pulsar", "quartz", "quiver", "radiance", "raven", "reef",
        "ridge", "river", "ruby", "saber", "safari", "sapphire", "shadow", "shield", "sierra", "silver",
        "solar", "spark", "spectrum", "spiral", "summit", "tempest", "thunder", "timber", "titan", "topaz",
        "torque", "tower", "trace", "tropic", "tundra", "valley", "vanguard", "vector", "velvet", "vessel",
        "vigor", "viper", "vortex", "voyage", "walnut", "warden", "wave", "whisper", "wild", "willow",
        "zenith", "zephyr", "zodiac"
    )

    fun generateHexToken(byteLength: Int): GeneratedTokenResult {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        val entropy = byteLength * 8.0
        val shannon = EntropyEngine.calculateTextEntropy(token).entropyBitsPerByte

        return GeneratedTokenResult(
            token = token,
            tokenType = "Hex Token (${byteLength * 8}-bit)",
            entropyBits = entropy,
            shannonEntropyPerByte = shannon,
            strengthRating = assessEntropyStrength(entropy),
            bruteForceEstimate = estimateBruteForceTime(entropy)
        )
    }

    fun generateBase64Token(byteLength: Int): GeneratedTokenResult {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        val token = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val entropy = byteLength * 8.0
        val shannon = EntropyEngine.calculateTextEntropy(token).entropyBitsPerByte

        return GeneratedTokenResult(
            token = token,
            tokenType = "Base64 Seed (${byteLength * 8}-bit)",
            entropyBits = entropy,
            shannonEntropyPerByte = shannon,
            strengthRating = assessEntropyStrength(entropy),
            bruteForceEstimate = estimateBruteForceTime(entropy)
        )
    }

    fun generatePassword(
        length: Int,
        includeUpper: Boolean = true,
        includeLower: Boolean = true,
        includeDigits: Boolean = true,
        includeSymbols: Boolean = true
    ): GeneratedTokenResult {
        val charPool = StringBuilder().apply {
            if (includeUpper) append(UPPERCASE)
            if (includeLower) append(LOWERCASE)
            if (includeDigits) append(DIGITS)
            if (includeSymbols) append(SYMBOLS)
        }.toString()

        val effectivePool = if (charPool.isEmpty()) LOWERCASE + DIGITS else charPool
        val passwordChars = CharArray(length)
        for (i in 0 until length) {
            passwordChars[i] = effectivePool[secureRandom.nextInt(effectivePool.length)]
        }
        val token = String(passwordChars)
        val poolSize = effectivePool.length
        val entropyBits = length * (ln(poolSize.toDouble()) / LOG_2)
        val shannon = EntropyEngine.calculateTextEntropy(token).entropyBitsPerByte

        return GeneratedTokenResult(
            token = token,
            tokenType = "Alphanumeric ($length chars, pool: $poolSize)",
            entropyBits = entropyBits,
            shannonEntropyPerByte = shannon,
            strengthRating = assessEntropyStrength(entropyBits),
            bruteForceEstimate = estimateBruteForceTime(entropyBits)
        )
    }

    fun generatePassphrase(wordCount: Int = 5, separator: String = "-"): GeneratedTokenResult {
        val chosenWords = List(wordCount) {
            DICEWARE_WORDLIST[secureRandom.nextInt(DICEWARE_WORDLIST.size)]
        }
        val token = chosenWords.joinToString(separator)
        val poolSize = DICEWARE_WORDLIST.size
        val entropyBits = wordCount * (ln(poolSize.toDouble()) / LOG_2)
        val shannon = EntropyEngine.calculateTextEntropy(token).entropyBitsPerByte

        return GeneratedTokenResult(
            token = token,
            tokenType = "Diceware Passphrase ($wordCount words)",
            entropyBits = entropyBits,
            shannonEntropyPerByte = shannon,
            strengthRating = assessEntropyStrength(entropyBits),
            bruteForceEstimate = estimateBruteForceTime(entropyBits)
        )
    }

    private fun assessEntropyStrength(bits: Double): String = when {
        bits < 40.0 -> "Very Weak (Vulnerable to instant offline crack)"
        bits < 64.0 -> "Moderate (Vulnerable to distributed GPU clusters)"
        bits < 96.0 -> "Strong (Resistant to specialized ASIC hardware)"
        bits < 128.0 -> "Very Strong (Enterprise military grade)"
        else -> "Quantum Resistant / Maximum Security"
    }

    private fun estimateBruteForceTime(bits: Double): String = when {
        bits < 40.0 -> "Less than 1 second (100 GH/s)"
        bits < 60.0 -> "~2 hours on modern GPU cluster"
        bits < 80.0 -> "~1,200 years on high-end cluster"
        bits < 120.0 -> "Billions of years (Extinction grade)"
        else -> "Heat death of the universe (>10^30 years)"
    }
}
