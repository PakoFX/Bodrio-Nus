package com.pako.eminus4.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.card.MaterialCardView
import com.pako.eminus4.R
import com.pako.eminus4.core.SessionManager
import com.pako.eminus4.data.api.EminusApiClient
import com.pako.eminus4.data.model.NotificationSummary

class NotificationsFragment : Fragment() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: EminusApiClient

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var cardStatusBanner: MaterialCardView
    private lateinit var txtStatusEmoji: TextView
    private lateinit var txtStatusTitle: TextView
    private lateinit var txtStatusDesc: TextView

    private lateinit var txtBadgeActNuevas: TextView
    private lateinit var txtActividadesActivas: TextView

    private lateinit var txtBadgeExNuevos: TextView
    private lateinit var txtExamenesActivos: TextView

    private lateinit var txtBadgeFrNuevos: TextView
    private lateinit var txtForosActivos: TextView

    private lateinit var txtBadgeMsjNuevos: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        apiClient = EminusApiClient(sessionManager)

        inicializarVistas(view)
        cargarNotificaciones()
    }

    private fun inicializarVistas(view: View) {
        val appBarLayout = view.findViewById<View>(R.id.appBarLayout)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        cardStatusBanner = view.findViewById(R.id.cardStatusBanner)
        txtStatusEmoji = view.findViewById(R.id.txtStatusEmoji)
        txtStatusTitle = view.findViewById(R.id.txtStatusTitle)
        txtStatusDesc = view.findViewById(R.id.txtStatusDesc)

        txtBadgeActNuevas = view.findViewById(R.id.txtBadgeActNuevas)
        txtActividadesActivas = view.findViewById(R.id.txtActividadesActivas)

        txtBadgeExNuevos = view.findViewById(R.id.txtBadgeExNuevos)
        txtExamenesActivos = view.findViewById(R.id.txtExamenesActivos)

        txtBadgeFrNuevos = view.findViewById(R.id.txtBadgeFrNuevos)
        txtForosActivos = view.findViewById(R.id.txtForosActivos)

        txtBadgeMsjNuevos = view.findViewById(R.id.txtBadgeMsjNuevos)

        swipeRefreshLayout.setColorSchemeColors(requireContext().getColor(android.R.color.holo_green_dark))
        swipeRefreshLayout.setOnRefreshListener {
            cargarNotificaciones()
        }
    }

    private fun cargarNotificaciones() {
        swipeRefreshLayout.isRefreshing = true

        apiClient.getNotifications { result ->
            if (!isAdded) return@getNotifications
            swipeRefreshLayout.isRefreshing = false

            result.onSuccess { summary ->
                mostrarNotificaciones(summary)
            }.onFailure { error ->
                Toast.makeText(requireContext(), "Error al cargar notificaciones: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mostrarNotificaciones(s: NotificationSummary) {
        // Banner de estado general
        val totalAlertas = s.getTotalAlertas()
        if (totalAlertas > 0) {
            txtStatusEmoji.text = "⚡"
            txtStatusTitle.text = "$totalAlertas pendiente${if (totalAlertas > 1) "s" else ""} por atender"
            txtStatusDesc.text = "Revisa los entregables y evaluaciones activas a continuación."
            cardStatusBanner.strokeColor = android.graphics.Color.parseColor("#38BDF8")
        } else {
            txtStatusEmoji.text = "✨"
            txtStatusTitle.text = "Al corriente"
            txtStatusDesc.text = "No tienes avisos urgentes sin revisar en tus cursos."
            cardStatusBanner.strokeColor = android.graphics.Color.parseColor("#CBD5E1")
        }

        // Actividades
        txtActividadesActivas.text = "${s.actActivas} activa${if (s.actActivas == 1) "" else "s"}"
        txtBadgeActNuevas.text = "${s.actNuevas} nueva${if (s.actNuevas == 1) "" else "s"}"
        txtBadgeActNuevas.visibility = if (s.actNuevas > 0) View.VISIBLE else View.GONE

        // Exámenes
        txtExamenesActivos.text = "${s.exActivos} activo${if (s.exActivos == 1) "" else "s"}"
        txtBadgeExNuevos.text = "${s.exNuevos} nuevo${if (s.exNuevos == 1) "" else "s"}"
        txtBadgeExNuevos.visibility = if (s.exNuevos > 0) View.VISIBLE else View.GONE

        // Foros
        txtForosActivos.text = "${s.frActivos} activo${if (s.frActivos == 1) "" else "s"}"
        txtBadgeFrNuevos.text = "${s.frNuevos} nuevo${if (s.frNuevos == 1) "" else "s"}"
        txtBadgeFrNuevos.visibility = if (s.frNuevos > 0) View.VISIBLE else View.GONE

        // Mensajes
        txtBadgeMsjNuevos.text = "${s.msjNuevos} sin leer"
        txtBadgeMsjNuevos.visibility = if (s.msjNuevos > 0) View.VISIBLE else View.GONE
    }
}

