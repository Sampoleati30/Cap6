package fr.cap6.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.*

class MainActivity : Activity(), LocationListener {
    private lateinit var locationManager: LocationManager
    private lateinit var map: MarineView
    private lateinit var coast: TextView
    private lateinit var speed: TextView
    private lateinit var heading: TextView
    private lateinit var gps: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        setContentView(buildScreen())
        AlertDialog.Builder(this)
            .setTitle("CAP 6 — version d’essai")
            .setMessage("CAP 6 est une aide à la navigation. Cette première version utilise un trait de côte prototype et ne remplace pas une carte marine officielle.")
            .setPositiveButton("Compris", null)
            .show()
        requestGps()
    }

    private fun buildScreen(): View {
        val root = FrameLayout(this)
        map = MarineView()
        root.addView(map, FrameLayout.LayoutParams(-1, -1))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(8, 14, 8, 14)
            setBackgroundColor(Color.rgb(7, 32, 50))
        }
        coast = metric("DISTANCE CÔTE\n—", 1.45f)
        speed = metric("VITESSE\n— nd", 1f)
        heading = metric("CAP GPS\n—", 1f)
        gps = metric("GPS\nRecherche", 1f)
        listOf(coast to 1.45f, speed to 1f, heading to 1f, gps to 1f).forEach {
            panel.addView(it.first, LinearLayout.LayoutParams(0, -2, it.second))
        }
        root.addView(panel, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(7, 32, 50))
        }
        buttons.addView(action("GPS") { requestGps() })
        buttons.addView(action("Pêche ici") {
            message("PÊCHE — INFORMATION INSUFFISANTE", "Aucune base réglementaire officielle complète n’est encore installée. Vérifiez l’arrêté en vigueur avant de pêcher.")
        })
        buttons.addView(action("6 NM") {
            message("6 NM DU LITTORAL", "Repère informatif : 6 NM = 11,112 km. Le permis côtier se réfère à un abri accessible, pas uniquement au rivage.")
        })
        buttons.addView(action("VHF / SOS") { showEmergency() })
        root.addView(buttons, FrameLayout.LayoutParams(-1, 84, Gravity.BOTTOM))
        return root
    }

    private fun metric(textValue: String, weight: Float) = TextView(this).apply {
        text = textValue
        setTextColor(Color.WHITE)
        textSize = if (weight > 1f) 16f else 13f
        gravity = Gravity.CENTER
    }

    private fun action(label: String, block: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener { block() }
        layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
    }

    private fun requestGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 6)
            return
        }
        gps.text = "GPS\nRecherche"
        runCatching {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this)
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(::onLocationChanged)
        }.onFailure { gps.text = "GPS\nIndisponible" }
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 6 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) requestGps()
        else gps.text = "GPS\nRefusé"
    }

    override fun onLocationChanged(location: Location) {
        val point = Point(location.latitude, location.longitude)
        val meters = distanceToCoast(point)
        coast.text = if (meters < 1000) String.format(Locale.FRANCE, "DISTANCE CÔTE\n%.2f NM\n%.0f m", meters / 1852.0, meters)
        else String.format(Locale.FRANCE, "DISTANCE CÔTE\n%.2f NM\n%.2f km", meters / 1852.0, meters / 1000.0)
        speed.text = String.format(Locale.FRANCE, "VITESSE\n%.1f nd", location.speed * 1.943844)
        heading.text = if (location.speed * 1.943844 < 1.0) "CAP GPS\n—\nImmobile" else String.format(Locale.FRANCE, "CAP GPS\n%03.0f°", location.bearing)
        gps.text = when {
            location.accuracy <= 8 -> "GPS\nPrécis ±${location.accuracy.toInt()} m"
            location.accuracy <= 25 -> "GPS\nMoyen ±${location.accuracy.toInt()} m"
            else -> "GPS\nFaible ±${location.accuracy.toInt()} m"
        }
        map.boat = point
        map.invalidate()
    }

    override fun onProviderDisabled(provider: String) { gps.text = "GPS\nDésactivé" }

    private fun showEmergency() {
        val p = map.boat
        val position = if (p == null) "Position GPS indisponible" else String.format(Locale.US, "%.5f, %.5f", p.lat, p.lon)
        message("VHF / SOS — CANAL 16", "PAN PAN PAN\nICI [NOM DU BATEAU]\nPosition : $position\nNature du problème : [PANNE / DÉRIVE]\nPersonnes à bord : [À COMPLÉTER]\n\nTéléphone CROSS : 196\nUrgence : 112")
    }

    private fun message(title: String, body: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("Fermer", null).show()
    }

    private fun distanceToCoast(p: Point): Double {
        var best = Double.POSITIVE_INFINITY
        for (i in 0 until COAST.lastIndex) best = min(best, pointSegmentMeters(p, COAST[i], COAST[i + 1]))
        return best
    }

    private fun pointSegmentMeters(p: Point, a: Point, b: Point): Double {
        val kx = 111320.0 * cos(Math.toRadians(p.lat))
        val ky = 110540.0
        val ax = (a.lon - p.lon) * kx
        val ay = (a.lat - p.lat) * ky
        val bx = (b.lon - p.lon) * kx
        val by = (b.lat - p.lat) * ky
        val dx = bx - ax
        val dy = by - ay
        val t = if (dx * dx + dy * dy == 0.0) 0.0 else (-(ax * dx + ay * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        return hypot(ax + t * dx, ay + t * dy)
    }

    inner class MarineView : View(this) {
        var boat: Point? = null
        private val sea = Paint().apply { color = Color.rgb(184, 224, 239) }
        private val land = Paint().apply { color = Color.rgb(234, 221, 177); style = Paint.Style.FILL }
        private val line = Paint().apply { color = Color.rgb(29, 74, 91); strokeWidth = 5f; style = Paint.Style.STROKE; isAntiAlias = true }
        private val boatPaint = Paint().apply { color = Color.rgb(220, 30, 40); style = Paint.Style.FILL; isAntiAlias = true }
        private val textPaint = Paint().apply { color = Color.DKGRAY; textSize = 28f; isAntiAlias = true }

        override fun onDraw(c: Canvas) {
            c.drawColor(sea.color)
            val top = 95f
            val bottom = height - 95f
            fun x(lon: Double) = (((lon - 3.0) / 4.55) * width).toFloat()
            fun y(lat: Double) = (bottom - ((lat - 42.35) / 1.45) * (bottom - top)).toFloat()
            val path = android.graphics.Path()
            COAST.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.lon), y(p.lat)) else path.lineTo(x(p.lon), y(p.lat)) }
            path.lineTo(width.toFloat(), top)
            path.lineTo(0f, top)
            path.close()
            c.drawPath(path, land)
            val coastPath = android.graphics.Path()
            COAST.forEachIndexed { i, p -> if (i == 0) coastPath.moveTo(x(p.lon), y(p.lat)) else coastPath.lineTo(x(p.lon), y(p.lat)) }
            c.drawPath(coastPath, line)
            c.drawText("MÉDITERRANÉE FRANÇAISE — CARTE PROTOTYPE", 24f, top + 36f, textPaint)
            boat?.let { c.drawCircle(x(it.lon), y(it.lat), 15f, boatPaint) }
        }
    }

    data class Point(val lat: Double, val lon: Double)

    companion object {
        val COAST = listOf(
            Point(42.44, 3.18), Point(42.59, 3.04), Point(42.97, 3.06), Point(43.09, 3.10),
            Point(43.25, 3.25), Point(43.28, 3.53), Point(43.37, 3.64), Point(43.53, 3.96),
            Point(43.56, 4.12), Point(43.45, 4.43), Point(43.35, 4.82), Point(43.39, 5.01),
            Point(43.30, 5.36), Point(43.21, 5.47), Point(43.15, 5.75), Point(43.06, 5.87),
            Point(43.08, 6.00), Point(43.12, 6.34), Point(43.17, 6.54), Point(43.27, 6.67),
            Point(43.42, 6.75), Point(43.49, 7.06), Point(43.59, 7.13), Point(43.70, 7.29),
            Point(43.74, 7.45)
        )
    }
}
