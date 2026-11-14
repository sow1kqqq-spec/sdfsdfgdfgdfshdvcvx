package ai.venice.nfccards

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Record(val type: String, val value: String)

data class Card(
    val id: String,
    val uid: String,
    val label: String,
    val kind: String,
    val records: List<Record>,
    val createdAt: Long
)

data class PassEvent(val ts: Long, val uid: String, val cardId: String?, val ok: Boolean)

object Store {
    private const val PREFS = "nfc_cards_v1"
    private fun sp(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun cards(c: Context): MutableList<Card> {
        val out = mutableListOf<Card>()
        val arr = try { JSONArray(sp(c).getString("cards", "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val recs = mutableListOf<Record>()
            val ra = o.optJSONArray("records") ?: JSONArray()
            for (j in 0 until ra.length()) {
                val r = ra.optJSONObject(j) ?: continue
                recs += Record(r.optString("type"), r.optString("value"))
            }
            out += Card(
                o.optString("id"), o.optString("uid"),
                o.optString("label", "Без названия"), o.optString("kind", "метка"),
                recs, o.optLong("createdAt", System.currentTimeMillis())
            )
        }
        return out
    }

    fun saveCards(c: Context, list: List<Card>) {
        val arr = JSONArray()
        for (card in list) {
            val o = JSONObject()
            o.put("id", card.id); o.put("uid", card.uid); o.put("label", card.label)
            o.put("kind", card.kind); o.put("createdAt", card.createdAt)
            val ra = JSONArray()
            for (r in card.records) ra.put(JSONObject().put("type", r.type).put("value", r.value))
            o.put("records", ra)
            arr.put(o)
        }
        sp(c).edit().putString("cards", arr.toString()).apply()
    }

    fun findById(c: Context, id: String) = cards(c).firstOrNull { it.id == id }

    fun upsert(c: Context, card: Card) {
        val list = cards(c).filterNot { it.id == card.id }.toMutableList()
        list.add(0, card)
        saveCards(c, list)
    }

    fun newCard(uid: String, label: String, kind: String, records: List<Record>) =
        Card(UUID.randomUUID().toString(), uid, label, kind, records, System.currentTimeMillis())

    fun delete(c: Context, id: String) = saveCards(c, cards(c).filterNot { it.id == id })

    fun logs(c: Context): MutableList<PassEvent> {
        val out = mutableListOf<PassEvent>()
        val arr = try { JSONArray(sp(c).getString("logs", "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += PassEvent(o.optLong("ts"), o.optString("uid"), o.optString("cardId").ifEmpty { null }, o.optBoolean("ok"))
        }
        return out
    }

    fun addLog(c: Context, e: PassEvent) {
        val list = logs(c)
        list.add(0, e)
        val arr = JSONArray()
        for (l in list.take(300)) {
            arr.put(JSONObject().put("ts", l.ts).put("uid", l.uid).put("cardId", l.cardId ?: "").put("ok", l.ok))
        }
        sp(c).edit().putString("logs", arr.toString()).apply()
    }

    fun clearLogs(c: Context) = sp(c).edit().putString("logs", "[]").apply()
}
