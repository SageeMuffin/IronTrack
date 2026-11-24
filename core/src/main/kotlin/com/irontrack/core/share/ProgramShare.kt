package com.irontrack.core.share

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/*
 * Partage d'un programme par lien, sans serveur : le programme complet est sérialisé en JSON compact,
 * compressé (deflate) puis encodé en base64 « URL-safe » directement dans le lien.
 * Les valeurs absentes restent absentes (jamais remplacées par 0).
 */

@Serializable
data class SharedSet(
    @SerialName("a") val repsMin: Int? = null,
    @SerialName("b") val repsMax: Int? = null,
    @SerialName("i") val rir: Int? = null,
    @SerialName("j") val rirMax: Int? = null,
    @SerialName("p") val rpe: Double? = null,
    @SerialName("w") val weightKg: Double? = null,
    @SerialName("r") val restSeconds: Int? = null,
)

@Serializable
data class SharedExercise(
    @SerialName("n") val name: String,
    /** Clé du catalogue intégré, pour retrouver le même exercice chez le destinataire. */
    @SerialName("k") val catalogKey: String? = null,
    @SerialName("m") val muscleGroup: String? = null,
    @SerialName("q") val equipment: String? = null,
    @SerialName("o") val notes: String? = null,
    @SerialName("r") val restSeconds: Int? = null,
    @SerialName("t") val target: SharedSet? = null,
    @SerialName("s") val sets: List<SharedSet> = emptyList(),
)

@Serializable
data class SharedSession(
    @SerialName("n") val name: String,
    @SerialName("w") val week: String = "A",
    @SerialName("d") val dayOfWeek: Int? = null,
    @SerialName("h") val timeOfDayMinutes: Int? = null,
    @SerialName("e") val estimatedDurationMinutes: Int? = null,
    @SerialName("on") val enabled: Boolean = true,
    @SerialName("o") val notes: String? = null,
    @SerialName("x") val exercises: List<SharedExercise> = emptyList(),
)

@Serializable
data class SharedProgram(
    @SerialName("n") val name: String,
    @SerialName("ab") val abEnabled: Boolean = false,
    @SerialName("o") val notes: String? = null,
    @SerialName("s") val sessions: List<SharedSession> = emptyList(),
) {
    val exerciseCount: Int get() = sessions.sumOf { it.exercises.size }
}

class ShareFormatException(message: String) : Exception(message)

object ProgramShareCodec {
    /** Adresse de la page qui ouvre le lien dans IronTrack (GitHub Pages du dépôt). */
    const val DEFAULT_BASE_URL = "https://sageemuffin.github.io/IronTrack/p/"
    private const val VERSION = "1"
    private const val MAX_ENCODED = 300_000
    private const val MAX_JSON_BYTES = 2_000_000

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }

    private val payloadRegex = Regex("""(?:#|[?&]d=)($VERSION\.[A-Za-z0-9_-]{8,})""")

    fun encode(program: SharedProgram): String {
        val bytes = json.encodeToString(SharedProgram.serializer(), program).toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(bytes)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        return VERSION + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    fun link(program: SharedProgram, baseUrl: String = DEFAULT_BASE_URL): String = baseUrl + "#" + encode(program)

    /** Extrait la donnée d'un lien IronTrack (https ou irontrack://) présent dans un texte, ou null. */
    fun extractPayload(text: String): String? {
        if (!text.contains("irontrack", ignoreCase = true)) return null
        return payloadRegex.find(text)?.groupValues?.get(1)
    }

    fun containsLink(text: String): Boolean = extractPayload(text) != null

    /** Décode un lien ou une donnée brute « 1.xxxx ». */
    fun decode(linkOrPayload: String): SharedProgram {
        val payload = extractPayload(linkOrPayload)
            ?: linkOrPayload.trim().takeIf { it.startsWith("$VERSION.") }
            ?: throw ShareFormatException("Lien de programme introuvable")
        if (payload.length > MAX_ENCODED) throw ShareFormatException("Lien trop long")
        val raw = try {
            Base64.getUrlDecoder().decode(payload.substringAfter('.'))
        } catch (e: IllegalArgumentException) {
            throw ShareFormatException("Lien incomplet ou abîmé")
        }
        val inflater = Inflater(true)
        inflater.setInput(raw)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buf, 0, n)
                if (out.size() > MAX_JSON_BYTES) throw ShareFormatException("Programme trop volumineux")
            }
        } catch (e: java.util.zip.DataFormatException) {
            throw ShareFormatException("Lien incomplet ou abîmé")
        } finally {
            inflater.end()
        }
        val program = try {
            json.decodeFromString(SharedProgram.serializer(), out.toString(Charsets.UTF_8.name()))
        } catch (e: Exception) {
            throw ShareFormatException("Lien incomplet ou abîmé")
        }
        return sanitize(program)
    }

    /** Valide et borne les valeurs reçues : un lien vient d'une source extérieure. */
    private fun sanitize(p: SharedProgram): SharedProgram {
        fun text(s: String?, max: Int) = s?.trim()?.take(max)?.takeIf { it.isNotEmpty() }
        fun int(v: Int?, min: Int, max: Int) = v?.takeIf { it in min..max }
        fun dbl(v: Double?, min: Double, max: Double) = v?.takeIf { !it.isNaN() && it in min..max }
        fun set(s: SharedSet) = SharedSet(
            repsMin = int(s.repsMin, 0, 1000), repsMax = int(s.repsMax, 0, 1000),
            rir = int(s.rir, 0, 10), rirMax = int(s.rirMax, 0, 10),
            rpe = dbl(s.rpe, 1.0, 10.0), weightKg = dbl(s.weightKg, 0.0, 2000.0),
            restSeconds = int(s.restSeconds, 0, 3600),
        )
        if (p.sessions.size > 100) throw ShareFormatException("Programme trop volumineux")
        return SharedProgram(
            name = text(p.name, 120) ?: throw ShareFormatException("Programme sans nom"),
            abEnabled = p.abEnabled,
            notes = text(p.notes, 5000),
            sessions = p.sessions.map { s ->
                if (s.exercises.size > 100) throw ShareFormatException("Programme trop volumineux")
                SharedSession(
                    name = text(s.name, 120) ?: "Séance",
                    week = if (p.abEnabled && s.week == "B") "B" else "A",
                    dayOfWeek = int(s.dayOfWeek, 1, 7),
                    timeOfDayMinutes = int(s.timeOfDayMinutes, 0, 24 * 60 - 1),
                    estimatedDurationMinutes = int(s.estimatedDurationMinutes, 1, 24 * 60),
                    enabled = s.enabled,
                    notes = text(s.notes, 2000),
                    exercises = s.exercises.mapNotNull { e ->
                        val name = text(e.name, 120) ?: return@mapNotNull null
                        if (e.sets.size > 50) throw ShareFormatException("Programme trop volumineux")
                        SharedExercise(
                            name = name,
                            catalogKey = text(e.catalogKey, 80),
                            muscleGroup = text(e.muscleGroup, 80),
                            equipment = text(e.equipment, 80),
                            notes = text(e.notes, 2000),
                            restSeconds = int(e.restSeconds, 0, 3600),
                            target = e.target?.let(::set),
                            sets = e.sets.map(::set),
                        )
                    },
                )
            },
        )
    }
}
