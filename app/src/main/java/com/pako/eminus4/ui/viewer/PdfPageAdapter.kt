package com.pako.eminus4.ui.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.pako.eminus4.R
import java.util.concurrent.Executors

class PdfPageAdapter(
    private val renderer: PdfRenderer,
    private val screenWidth: Int
) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Caché en memoria para bitmaps de páginas ya renderizadas
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    private val pageBitmapCache = object : LruCache<Int, Bitmap>(cacheSize) {
        override fun sizeOf(key: Int, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    override fun getItemCount(): Int = renderer.pageCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val cached = pageBitmapCache.get(position)
        if (cached != null) {
            holder.imageView.setImageBitmap(cached)
            return
        }

        // Placeholder blanco mientras renderiza
        holder.imageView.setImageBitmap(null)
        holder.imageView.setBackgroundColor(Color.WHITE)

        val targetWidth = if (screenWidth > 0) screenWidth else 1080

        executor.execute {
            var bitmap: Bitmap? = null
            synchronized(renderer) {
                try {
                    val page = renderer.openPage(position)
                    val aspectRatio = page.height.toFloat() / page.width.toFloat()
                    val targetHeight = (targetWidth * aspectRatio).toInt()

                    bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    page.render(bitmap!!, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            bitmap?.let { bmp ->
                pageBitmapCache.put(position, bmp)
                mainHandler.post {
                    if (holder.bindingAdapterPosition == position) {
                        holder.imageView.setImageBitmap(bmp)
                    }
                }
            }
        }
    }

    fun release() {
        executor.shutdown()
        pageBitmapCache.evictAll()
    }

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imgPdfPage)
    }
}

