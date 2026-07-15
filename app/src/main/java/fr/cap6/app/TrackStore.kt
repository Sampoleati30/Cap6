package fr.cap6.app

import android.content.Context

class TrackStore(context: Context) {
    private val database = LocalAppDatabase(context.applicationContext)
    fun state(): TrackState = database.trackState()
    fun setState(state: TrackState) = database.setTrackState(state)
    fun load(): List<TrackPoint> = database.loadTrackPoints()
    @Synchronized fun append(point: TrackPoint): Boolean = database.appendTrackPoint(point)
    fun clear() = database.clearTrack()
    fun summary(): TrackSummary {
        val recorder = TrackRecorder()
        recorder.restore(load())
        return recorder.summary()
    }
    fun export(name: String): String = GpxCodec.exportTrack(name, load())
}
