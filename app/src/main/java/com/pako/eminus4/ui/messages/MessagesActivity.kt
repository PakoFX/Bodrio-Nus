package com.pako.eminus4.ui.messages

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.pako.eminus4.R
import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.data.api.EminusApiClient

class MessagesActivity : AppCompatActivity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, MessagesActivity::class.java)
            context.startActivity(intent)
        }
    }

    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: EminusApiClient

    private lateinit var toolbar: MaterialToolbar
    private lateinit var tabFolders: TabLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerMessages: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var txtEmptyTitle: TextView
    private lateinit var txtEmptySubtitle: TextView

    private lateinit var adapter: MessageAdapter
    private var currentFolderId: Int = 1 // 1: Recibidos, 2: Enviados

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messages)

        sessionManager = SessionManager(this)
        apiClient = EminusApiClient(sessionManager)

        inicializarVistas()
        cargarMensajes()
    }

    private fun inicializarVistas() {
        toolbar = findViewById(R.id.toolbar)
        val appBarLayout = findViewById<View>(R.id.appBarLayout)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        toolbar.setNavigationOnClickListener { finish() }

        tabFolders = findViewById(R.id.tabFolders)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        recyclerMessages = findViewById(R.id.recyclerMessages)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        txtEmptyTitle = findViewById(R.id.txtEmptyTitle)
        txtEmptySubtitle = findViewById(R.id.txtEmptySubtitle)

        swipeRefresh.setColorSchemeColors(Color.parseColor("#00897B"))
        swipeRefresh.setOnRefreshListener {
            cargarMensajes()
        }

        adapter = MessageAdapter { item ->
            MessageDetailActivity.start(this, item.idMensaje)
        }

        recyclerMessages.layoutManager = LinearLayoutManager(this)
        recyclerMessages.adapter = adapter

        tabFolders.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentFolderId = if (tab?.position == 1) 2 else 1
                cargarMensajes()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun cargarMensajes() {
        swipeRefresh.isRefreshing = true

        apiClient.getMessages(currentFolderId) { result ->
            swipeRefresh.isRefreshing = false

            result.onSuccess { list ->
                adapter.submitList(list)
                if (list.isEmpty()) {
                    layoutEmpty.visibility = View.VISIBLE
                    if (currentFolderId == 1) {
                        txtEmptyTitle.text = "Bandeja vacía"
                        txtEmptySubtitle.text = "No tienes mensajes recibidos por el momento."
                    } else {
                        txtEmptyTitle.text = "Sin mensajes enviados"
                        txtEmptySubtitle.text = "No has enviado mensajes institucionales aún."
                    }
                } else {
                    layoutEmpty.visibility = View.GONE
                }
            }.onFailure { error ->
                Toast.makeText(this, "Error al cargar mensajes: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

