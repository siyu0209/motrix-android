package app.motrix.android.ui

import android.app.Activity
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText
import app.motrix.android.R

/** 新建下载对话框：输入 URL / 磁力链，或选择 .torrent 文件。 */
object AddTaskDialog {

    fun show(
        activity: Activity,
        onAddUrl: (String) -> Unit,
        onPickTorrent: () -> Unit
    ) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_add, null)
        val input = view.findViewById<TextInputEditText>(R.id.inputUrl)
        val btnPick = view.findViewById<Button>(R.id.btnPickTorrent)

        val dialog = AlertDialog.Builder(activity, R.style.ThemeOverlay_Motrix_Dialog)
            .setTitle(R.string.add_task)
            .setView(view)
            .setPositiveButton(R.string.download, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        btnPick.setOnClickListener {
            dialog.dismiss()
            onPickTorrent()
        }

        dialog.setOnShowListener {
            // 让对话框占满可用宽度，避免提示文字被窄宽截断
            val dm = activity.resources.displayMetrics
            dialog.window?.setLayout(
                (dm.widthPixels * 0.92).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener {
                val text = input.text?.toString()?.trim().orEmpty()
                if (TextUtils.isEmpty(text)) {
                    input.error = activity.getString(R.string.url_required)
                    return@setOnClickListener
                }
                // 传整段文本，由 Engine.extractUrls 从中提取链接（支持一行里带标题/提取码）
                onAddUrl(text)
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}
