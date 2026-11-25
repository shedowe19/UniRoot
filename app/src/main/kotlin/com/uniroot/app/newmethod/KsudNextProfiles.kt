package com.uniroot.app.newmethod

import android.content.Context
import java.io.File

/**
 * Profils ksud Next de la new méthode — choisissables sur l'accueil quand le
 * switch KernelSU Next est actif (version complète affichée : 3.3.0, 3.4.0…).
 *
 * DEUX SOURCES :
 *  1. EMBARQUÉS — assets df/ksud-next/<id> (+ <id>.txt optionnel). Ajouter un
 *     profil sans toucher au code = déposer le binaire là.
 *  2. DYNAMIQUES — filesDir/ksud-next/<id> : les ksud téléchargés + patchés
 *     par DfKsudUpdater (le bouton « Mettre à jour le ksud Next »). Le
 *     résultat du script atterrit ICI et apparaît directement dans la liste.
 *
 * La liste fusionne les deux sources, triée par version décroissante puis
 * slug ; le premier profil est le défaut. La sélection est persistée dans
 * uniroot_prefs/ksud_next_profile.
 */
data class KsudNextProfile(
    val id: String,        // nom du fichier, ex. "kdp-samsung-v3.4.2" ou "3.4.0-kdp-samsung"
    val version: String,   // version numérique pour le tri, ex. "3.4.2"
    val variant: String,   // variante, ex. "kdp-samsung" ("" si absent)
    val note: String,      // description (sidecar <id>.txt ou note dynamique)
    val dynamic: Boolean = false, // true = fichier téléchargé (filesDir)
    val label: String = if (variant.isEmpty()) version else "$version · $variant",
)

object KsudNextProfiles {

    internal data class ParsedProfileId(
        val version: String,
        val variant: String,
        val label: String,
    )

    /**
     * Parses both naming conventions:
     * - "kdp-samsung-v3.4.2" -> version="3.4.2", variant="kdp-samsung", label="kdp-samsung-v3.4.2"
     * - "3.4.0-kdp-samsung"  -> version="3.4.0", variant="kdp-samsung", label="3.4.0 · kdp-samsung"
     */
    internal fun parseProfileId(name: String): ParsedProfileId {
        val prefixed = Regex("^(.+)-v(\\d+(?:\\.\\d+)*)$").matchEntire(name)
        if (prefixed != null) {
            val variant = prefixed.groupValues[1]
            val version = prefixed.groupValues[2]
            return ParsedProfileId(version = version, variant = variant, label = name)
        }
        val dash = name.indexOf('-')
        val version = if (dash > 0) name.substring(0, dash) else name
        val variant = if (dash > 0) name.substring(dash + 1) else ""
        val label = if (variant.isEmpty()) version else "$version · $variant"
        return ParsedProfileId(version = version, variant = variant, label = label)
    }

    private const val DIR = "df/ksud-next"
    private const val PREFS = "uniroot_prefs"
    private const val PREF_KEY = "ksud_next_profile"
    private const val MODE = Context.MODE_PRIVATE

    /** Dossier des profils dynamiques (téléchargés par DfKsudUpdater). */
    fun dynamicDir(context: Context): File =
        File(context.filesDir, "ksud-next").apply { mkdirs() }

    /** Profils embarqués + dynamiques, triés version décroissante puis slug. */
    fun list(context: Context): List<KsudNextProfile> {
        val names: List<String> = runCatching {
            context.assets.list(DIR)?.toList() ?: emptyList()
        }.getOrDefault(emptyList())
        val embedded = names.filter { it.isNotEmpty() && !it.endsWith(".txt") }
            .map { name -> profileFromId(context, name, dynamic = false) }
        val dynamic = runCatching {
            dynamicDir(context).listFiles { f -> f.isFile && !f.name.endsWith(".txt") }
                ?.map { it.name }
                ?.map { name -> profileFromId(context, name, dynamic = true) }
                ?: emptyList()
        }.getOrDefault(emptyList())
        return (embedded + dynamic)
            .distinctBy { it.id }
            .sortedWith { a, b ->
                val ra = versionRank(a.version)
                val rb = versionRank(b.version)
                for (i in 0 until maxOf(ra.size, rb.size)) {
                    val x = ra.getOrElse(i) { 0 }
                    val y = rb.getOrElse(i) { 0 }
                    if (x != y) return@sortedWith y - x   // version décroissante
                }
                a.variant.compareTo(b.variant)
            }
    }

    private fun profileFromId(context: Context, name: String, dynamic: Boolean): KsudNextProfile {
        val parsed = parseProfileId(name)
        val note = if (dynamic) {
            runCatching { sidecar(context, name).readText().lineSequence().firstOrNull()?.trim() ?: "" }
                .getOrDefault("")
        } else {
            runCatching {
                context.assets.open("$DIR/$name.txt").bufferedReader().useLines { lines ->
                    lines.firstOrNull()?.trim() ?: ""
                }
            }.getOrDefault("")
        }
        return KsudNextProfile(
            id = name,
            version = parsed.version,
            variant = parsed.variant,
            note = note,
            dynamic = dynamic,
            label = parsed.label,
        )
    }

    private fun sidecar(context: Context, id: String): File =
        File(dynamicDir(context), "$id.txt")

    /** Profil affiché par défaut (versions les plus récentes d'abord). */
    fun default(context: Context): KsudNextProfile? = list(context).firstOrNull()

    fun selectedId(context: Context): String =
        context.getSharedPreferences(PREFS, MODE).getString(PREF_KEY, "") ?: ""

    /** Sélection persistée, sinon le défaut ; null si aucun profil. */
    fun selected(context: Context): KsudNextProfile? {
        val profiles = list(context)
        if (profiles.isEmpty()) return null
        return profiles.firstOrNull { it.id == selectedId(context) } ?: profiles.first()
    }

    fun setSelected(context: Context, id: String) {
        context.getSharedPreferences(PREFS, MODE).edit().putString(PREF_KEY, id).apply()
    }

    /**
     * Asset du profil choisi ("df/ksud-next/<id>") — profils embarqués
     * uniquement. null pour un profil dynamique (voir selectedFile).
     */
    fun selectedAsset(context: Context): String? {
        val p = selected(context) ?: return null
        return if (p.dynamic) null else "$DIR/${p.id}"
    }

    /** Fichier du profil dynamique choisi ; null si le profil est embarqué. */
    @JvmStatic
    fun selectedFile(context: Context): File? {
        val p = selected(context) ?: return null
        return if (p.dynamic) File(dynamicDir(context), p.id) else null
    }

    /**
     * Enregistre un profil dynamique (résultat de DfKsudUpdater) et le
     * SÉLECTIONNE — il apparaît immédiatement dans le sélecteur de l'accueil.
     */
    fun addDynamic(context: Context, id: String, bytes: ByteArray, note: String) {
        val f = File(dynamicDir(context), id)
        val tmp = File(f.path + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(f)) { tmp.delete(); throw IllegalStateException("rename $id") }
        sidecar(context, id).writeText(note)
        setSelected(context, id)
    }

    /** Point d'entrée Java (DFBridge). */
    @JvmStatic
    fun resolveAsset(context: Context): String? = selectedAsset(context)

    /** Deletes a DYNAMIC profile (binary + sidecar). false = absent/embedded. */
    fun deleteDynamic(context: Context, id: String): Boolean {
        val f = File(dynamicDir(context), id)
        if (!f.isFile) return false
        sidecar(context, id).delete()
        return f.delete()
    }

    /** "3.10.0" > "3.9.0" : comparaison numérique champ par champ. */
    private fun versionRank(version: String): List<Int> =
        version.split('.').map { it.toIntOrNull() ?: 0 }
}
