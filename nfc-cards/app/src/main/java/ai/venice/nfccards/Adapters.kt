package ai.venice.nfccards

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ai.venice.nfccards.databinding.ItemCardBinding

class CardAdapter(
    private val items: List<Card>,
    private val onClick: (Card) -> Unit,
    private val onLong: (Card) -> Unit
) : RecyclerView.Adapter<CardAdapter.VH>() {

    class VH(val b: ItemCardBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, pos: Int) {
        val c = items[pos]
        h.b.name.text = c.label
        h.b.uid.text = c.uid
        h.b.kind.text = c.kind
        h.b.root.setOnClickListener { onClick(c) }
        h.b.root.setOnLongClickListener { onLong(c); true }
    }

    override fun getItemCount() = items.size
}
