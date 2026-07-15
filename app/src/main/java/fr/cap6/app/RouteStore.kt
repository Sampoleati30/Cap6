package fr.cap6.app

import android.content.Context

class RouteStore(context: Context) {
    private val database = LocalAppDatabase(context.applicationContext)
    fun load(): PersistedRoute? = database.loadActiveRoute()?.let(RoutePersistenceCodec::decode)
    fun save(name: String, route: PlannedRoute) = database.saveActiveRoute(RoutePersistenceCodec.encode(name, route))
    fun clear() = database.clearActiveRoute()
}
