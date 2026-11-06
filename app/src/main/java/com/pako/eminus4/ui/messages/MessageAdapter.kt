package com.pako.eminus4.ui.messages

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pako.eminus4.R
import com.pako.eminus4.data.model.MessageItem
import java.text.SimpleDateFormat
import java.util.Locale

class MessageAdapter(
    private val onMessageClick: (MessageItem) -> Unit
) : ListAdapter<MessageItem, MessageAdapter.MessageViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dotUnread: View = itemView.findViewById(R.id.dotUnread)
        private val txtSender: TextView = itemView.findViewById(R.id.txtSender)
        private val txtDate: TextView = itemView.findViewById(R.id.txtDate)
        private val txtCourseTag: TextView = itemView.findViewById(R.id.txtCourseTag)
        private val txtSubject: TextView = itemView.findViewById(R.id.txtSubject)
        private val txtSnippet: TextView = itemView.findViewById(R.id.txtSnippet)

        fun bind(item: MessageItem) {
            txtSender.text = item.getNombreRemitente()
            txtDate.text = formatearFecha(item.fechaEnviado)
            txtCourseTag.text = item.getCursoLimpio()
            txtSubject.text = item.titulo ?: "(Sin asunto)"
            val snippet = item.contenidoRecortado ?: ""
            txtSnippet.text = androidx.core.text.HtmlCompat.fromHtml(snippet, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
            dotUnread.visibility = if (item.esNoLeido) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                onMessageClick(item)
            }
        }

        private fun formatearFecha(isoDate: String?): String {
            if (isoDate.isNullOrBlank()) return ""
            return try {
                val inputFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val outputFmt = SimpleDateFormat("dd MMM, HH:mm", Locale("es"))
                val date = inputFmt.parse(isoDate.substring(0, 19)) ?: return isoDate
                outputFmt.format(date)
            } catch (_: Exception) {
                isoDate
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<MessageItem>() {
        override fun areItemsTheSame(oldItem: MessageItem, newItem: MessageItem): Boolean {
            return oldItem.idMensaje == newItem.idMensaje
        }

        override fun areContentsTheSame(oldItem: MessageItem, newItem: MessageItem): Boolean {
            return oldItem == newItem
        }
    }
}
