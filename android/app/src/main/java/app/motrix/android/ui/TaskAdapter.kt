package app.motrix.android.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import app.motrix.android.R
import app.motrix.android.engine.DownloadTask
import app.motrix.android.util.Fmt

class TaskAdapter(
    private val onToggle: (DownloadTask) -> Unit,
    private val onRemove: (DownloadTask) -> Unit,
    private val onItemClick: (DownloadTask) -> Unit
) : RecyclerView.Adapter<TaskAdapter.VH>() {

    private val items = ArrayList<DownloadTask>()

    fun submit(list: List<DownloadTask>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val name: TextView = v.findViewById(R.id.txtName)
        private val status: TextView = v.findViewById(R.id.txtStatus)
        private val progress: LinearProgressIndicator = v.findViewById(R.id.progressBar)
        private val speed: TextView = v.findViewById(R.id.txtSpeed)
        private val btnAction: ImageButton = v.findViewById(R.id.btnAction)
        private val btnRemove: ImageButton = v.findViewById(R.id.btnRemove)

        fun bind(t: DownloadTask) {
            name.text = t.name
            status.text = when {
                t.isComplete -> "已完成 · ${Fmt.size(t.totalBytes)} · 点开可打开"
                t.isError -> "失败 · ${t.errorMessage.ifBlank { "未知错误" }}"
                t.isPaused -> "已暂停 · ${Fmt.size(t.doneBytes)} / ${Fmt.size(t.totalBytes)}"
                t.isWaiting -> "等待中 · ${Fmt.size(t.doneBytes)} / ${Fmt.size(t.totalBytes)}"
                else -> "${Fmt.size(t.doneBytes)} / ${Fmt.size(t.totalBytes)} · ${t.progress}%"
            }
            if (t.totalBytes > 0L) {
                progress.visibility = View.VISIBLE
                progress.isIndeterminate = false
                progress.setProgressCompat(t.progress, true)
            } else {
                progress.visibility = if (t.isActive) View.VISIBLE else View.GONE
                progress.isIndeterminate = true
            }
            val up = if (t.uploadSpeed > 0) " · ↑ ${Fmt.speed(t.uploadSpeed)}" else ""
            val loc = t.dir.ifBlank { t.path }
            speed.text = when {
                t.isActive -> "↓ ${Fmt.speed(t.downloadSpeed)}$up · ${Fmt.eta(t.doneBytes, t.totalBytes, t.downloadSpeed)}"
                t.isComplete && loc.isNotBlank() -> "已保存：$loc"
                t.isTorrent && t.numSeeders > 0 -> "做种 · ${t.numSeeders} 个连接"
                loc.isNotBlank() -> "位置：$loc"
                else -> ""
            }
            btnAction.visibility = if (t.isComplete) View.GONE else View.VISIBLE
            btnAction.setImageResource(
                if (t.isActive) R.drawable.ic_pause else R.drawable.ic_play
            )
            btnAction.setOnClickListener { onToggle(t) }
            btnRemove.setOnClickListener { onRemove(t) }
            itemView.setOnClickListener { onItemClick(t) }
        }
    }
}
