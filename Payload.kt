package ai.venice.nfccards

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Payload {
    fun build(card: Card, verdict: String, reason: String, charged: Double): String {
        val o = JSONObject()
        o.put("verdict", verdict)
        o.put("reason", reason)
        o.put("uid", card.uid)
        o.put("label", card.label)
        o.put("kind", card.kind)
        o.put("ts", System.currentTimeMillis())
        o.put("iso", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))
        o.put("charged", charged)
        o.put("balance", card.balance)
        o.put("tariff", card.tariff)
        val ra = JSONArray()
        for (r in card.records) ra.put(JSONObject().put("type", r.type).put("value", r.value))
        o.put("records", ra)
        return o.toString(2)
    }
}