package fr.cap6.app

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

class TrackLocationService : Service(), LocationListener {
    private lateinit var locationManager: LocationManager
    private lateinit var store: TrackStore

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        store = TrackStore(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recoveredAction = when (store.state()) {
            TrackState.RECORDING -> ACTION_RESUME
            TrackState.PAUSED -> ACTION_PAUSE
            TrackState.STOPPED -> ACTION_STOP
        }
        when (intent?.action ?: recoveredAction) {
            ACTION_START -> {
                store.setState(TrackState.RECORDING)
                startForegroundCompat("Trace active")
                requestUpdates()
            }
            ACTION_PAUSE -> {
                store.setState(TrackState.PAUSED)
                locationManager.removeUpdates(this)
                startForegroundCompat("Trace en pause")
            }
            ACTION_RESUME -> {
                store.setState(TrackState.RECORDING)
                startForegroundCompat("Trace active")
                requestUpdates()
            }
            ACTION_STOP -> {
                store.setState(TrackState.STOPPED)
                locationManager.removeUpdates(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_CLEAR -> {
                store.clear()
                locationManager.removeUpdates(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return if (store.state() == TrackState.STOPPED) START_NOT_STICKY else START_STICKY
    }

    private fun requestUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            store.setState(TrackState.STOPPED)
            stopSelf()
            return
        }
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            startForegroundCompat("GPS désactivé — trace en attente")
            return
        }
        runCatching {
            locationManager.removeUpdates(this)
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 1f, this)
        }.onFailure {
            startForegroundCompat("GPS indisponible — trace en attente")
        }
    }

    override fun onLocationChanged(location: Location) {
        if (store.state() != TrackState.RECORDING || location.accuracy > 150f) return
        store.append(
            TrackPoint(
                GeoPoint(location.latitude, location.longitude),
                location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
                location.speed.takeIf { location.hasSpeed() }?.toDouble()?.times(1.943844492),
                location.accuracy.toDouble()
            )
        )
    }

    @Deprecated("Deprecated in Android")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER && store.state() == TrackState.RECORDING) {
            startForegroundCompat("GPS désactivé — trace en attente")
        }
    }

    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER && store.state() == TrackState.RECORDING) requestUpdates()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.tracking_channel_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun startForegroundCompat(status: String) {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pauseAction = PendingIntent.getService(
            this, 1, Intent(this, TrackLocationService::class.java).setAction(if (store.state() == TrackState.PAUSED) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopAction = PendingIntent.getService(
            this, 2, Intent(this, TrackLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CAP 6 — $status")
            .setContentText("La trace est enregistrée uniquement sur l'appareil.")
            .setSmallIcon(R.drawable.ic_stat_navigation)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, if (store.state() == TrackState.PAUSED) "Reprendre" else "Pause", pauseAction)
            .addAction(0, "Arrêter", stopAction)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        locationManager.removeUpdates(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "fr.cap6.app.action.TRACK_START"
        const val ACTION_PAUSE = "fr.cap6.app.action.TRACK_PAUSE"
        const val ACTION_RESUME = "fr.cap6.app.action.TRACK_RESUME"
        const val ACTION_STOP = "fr.cap6.app.action.TRACK_STOP"
        const val ACTION_CLEAR = "fr.cap6.app.action.TRACK_CLEAR"
        private const val CHANNEL_ID = "cap6_tracking"
        private const val NOTIFICATION_ID = 60
    }
}
