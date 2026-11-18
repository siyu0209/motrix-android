package app.motrix.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 通知中心：展示下载完成/失败的历史记录。 */
class NotificationCenterActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        val tb = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(tb)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        tb.setNavigationOnClickListener { finish() }

        recycler = findViewById(R.id.recycler)
        empty = findViewById(R.id.empty)
        recycler.layoutManager = LinearLayoutManager(this)

        findViewById<MaterialButton>(R.id.btnClear).setOnClickListener {
            NotificationStore.clear(this)
            refresh()
        }
        refresh()
    }

    private fun refresh() {
        val items = NotificationStore.list(this)
        recycler.adapter = NotifAdapter(items)
        val isEmpty = items.isEmpty()
        empty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private inner class NotifAdapter(private val items: List<NotificationStore.Entry>) :
        RecyclerView.Adapter<NotifAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_notif, parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val e = items[position]
            holder.itemView.findViewById<TextView>(R.id.nTitle).text = e.name
            val df = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            var sub = "${e.status} · ${df.format(Date(e.time))}"
            if (!e.error.isNullOrBlank()) sub += " · ${e.error}"
            holder.itemView.findViewById<TextView>(R.id.nSub).text = sub
        }
    }
}
