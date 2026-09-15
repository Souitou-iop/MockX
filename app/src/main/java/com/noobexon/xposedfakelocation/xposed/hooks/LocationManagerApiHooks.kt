package com.noobexon.xposedfakelocation.xposed.hooks

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.reflect.Method
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Comprehensive hooks for [android.location.LocationManager] inside target application processes.
 *
 * Major domestic navigation and map apps (AMap, Baidu, Tencent) utilize multiple location acquisition
 * channels beyond `getLastKnownLocation`:
 * 1. Continuous updates via `requestLocationUpdates` with [LocationListener].
 * 2. Asynchronous single updates via `requestSingleUpdate` and `getCurrentLocation`.
 * 3. Raw GPS sentence parsing via NMEA callbacks (`addNmeaListener`, `registerGnssNmeaCallback`).
 * 4. GNSS satellite status callbacks (`registerGnssStatusCallback`).
 *
 * This class intercepts all pull and push location mechanisms, dynamically intercepts the target
 * app's [LocationListener] classes to guarantee spoofed coordinates, provides an active 1-second
 * heartbeat location dispatcher (preventing map apps from timing out and falling back to network
 * positioning), and silences raw NMEA/satellite signals that would otherwise leak real coordinates.
 */
class LocationManagerApiHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[LocationManagerApiHooks]"

    private val activeTickers = ConcurrentHashMap<Any, ScheduledFuture<*>>()
    private val hookedListenerClasses = Collections.synchronizedSet(HashSet<Class<*>>())
    private val registeredListeners = Collections.newSetFromMap(java.util.WeakHashMap<LocationListener, Boolean>())
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "FakeLoc-Heartbeat").apply { isDaemon = true }
    }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun init() {
        runCatching {
            val locationManagerClass = Class.forName("android.location.LocationManager", false, classLoader)
            hookLastKnownLocation(locationManagerClass)
            hookLastLocation(locationManagerClass)
            hookCurrentLocation(locationManagerClass)
            hookRequestLocationUpdates(locationManagerClass)
            hookRequestSingleUpdate(locationManagerClass)
            hookRemoveUpdates(locationManagerClass)
            hookNmeaListeners(locationManagerClass)
            hookGnssStatusCallbacks(locationManagerClass)
            hookGnssMeasurements(locationManagerClass)
            startContinuousHeartbeatLoop()
            module.log(Log.INFO, tag, "Instantiated all LocationManager hooks successfully")
        }.onFailure {
            module.log(Log.ERROR, tag, "Error initializing LocationManagerApiHooks: ${it.message}")
        }
    }

    /**
     * Intercepts [LocationManager.getLastKnownLocation] to return the spoofed location.
     */
    private fun hookLastKnownLocation(clazz: Class<*>) {
        hookAll(clazz, "getLastKnownLocation") { chain ->
            val provider = (chain.args.firstOrNull() as? String) ?: LocationManager.GPS_PROVIDER
            val original = chain.proceed() as? Location
            if (PreferencesUtil.getIsPlaying() == true) {
                LocationUtil.updateLocation()
                val fake = LocationUtil.createFakeLocation(originalLocation = original, provider = provider)
                module.log(Log.INFO, tag, "getLastKnownLocation -> spoofed: (${fake.latitude}, ${fake.longitude})")
                fake
            } else {
                original
            }
        }
    }

    /**
     * Intercepts [LocationManager.getLastLocation] (API 31+) to return the spoofed location.
     */
    private fun hookLastLocation(clazz: Class<*>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        hookAll(clazz, "getLastLocation") { chain ->
            val original = chain.proceed() as? Location
            if (PreferencesUtil.getIsPlaying() == true) {
                LocationUtil.updateLocation()
                val fake = LocationUtil.createFakeLocation(originalLocation = original, provider = LocationManager.FUSED_PROVIDER)
                module.log(Log.INFO, tag, "getLastLocation -> spoofed: (${fake.latitude}, ${fake.longitude})")
                fake
            } else {
                original
            }
        }
    }

    /**
     * Intercepts [LocationManager.getCurrentLocation] (API 30+) to dispatch the fake location
     * directly to the Consumer callback without waiting for system hardware.
     */
    private fun hookCurrentLocation(clazz: Class<*>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        hookAll(clazz, "getCurrentLocation") { chain ->
            if (PreferencesUtil.getIsPlaying() != true) {
                return@hookAll chain.proceed()
            }

            LocationUtil.updateLocation()
            val fake = LocationUtil.createFakeLocation(provider = LocationManager.FUSED_PROVIDER)

            // Signature: getCurrentLocation(provider/request, cancellationSignal, executor, consumer)
            val args = chain.args
            val consumer = args.lastOrNull { it != null && it.javaClass.methods.any { m -> m.name == "accept" && m.parameterTypes.size == 1 } }
            val executor = args.firstOrNull { it is java.util.concurrent.Executor } as? java.util.concurrent.Executor

            if (consumer != null) {
                val acceptMethod = consumer.javaClass.methods.first { it.name == "accept" && it.parameterTypes.size == 1 }
                val dispatchAction = Runnable {
                    runCatching {
                        acceptMethod.isAccessible = true
                        acceptMethod.invoke(consumer, fake)
                        module.log(Log.INFO, tag, "Dispatched spoofed location to getCurrentLocation Consumer.")
                    }.onFailure { module.log(Log.ERROR, tag, "Failed to invoke getCurrentLocation consumer: ${it.message}") }
                }

                if (executor != null) {
                    executor.execute(dispatchAction)
                } else {
                    mainHandler.post(dispatchAction)
                }
                return@hookAll null
            }

            chain.proceed()
        }
    }

    /**
     * Intercepts all overloads of [LocationManager.requestLocationUpdates].
     * 1. Extracts the [LocationListener] argument and ensures its class methods are hooked.
     * 2. Registers the listener into the active tracking pool for continuous heartbeats.
     */
    private fun hookRequestLocationUpdates(clazz: Class<*>) {
        hookAll(clazz, "requestLocationUpdates") { chain ->
            val listener = chain.args.firstOrNull { it is LocationListener } as? LocationListener
            if (listener != null) {
                hookListenerClassIfNeeded(listener.javaClass)
                synchronized(registeredListeners) {
                    registeredListeners.add(listener)
                }
            }
            chain.proceed()
        }
    }

    /**
     * Intercepts all overloads of [LocationManager.requestSingleUpdate].
     */
    private fun hookRequestSingleUpdate(clazz: Class<*>) {
        hookAll(clazz, "requestSingleUpdate") { chain ->
            val listener = chain.args.firstOrNull { it is LocationListener } as? LocationListener
            if (listener != null) {
                hookListenerClassIfNeeded(listener.javaClass)
                if (PreferencesUtil.getIsPlaying() == true) {
                    LocationUtil.updateLocation()
                    val fake = LocationUtil.createFakeLocation()
                    mainHandler.post {
                        runCatching { listener.onLocationChanged(fake) }
                    }
                }
            }
            chain.proceed()
        }
    }

    /**
     * Stops tracking the listener when the target app calls [LocationManager.removeUpdates].
     */
    private fun hookRemoveUpdates(clazz: Class<*>) {
        hookAll(clazz, "removeUpdates") { chain ->
            val listener = chain.args.firstOrNull { it is LocationListener }
            if (listener != null) {
                synchronized(registeredListeners) {
                    registeredListeners.remove(listener)
                }
                stopHeartbeat(listener)
            }
            chain.proceed()
        }
    }

    /**
     * Hooks raw NMEA listener registrations to prevent domestic map apps from reading
     * real coordinates directly from `$GPGGA` / `$GPRMC` sentences.
     */
    private fun hookNmeaListeners(clazz: Class<*>) {
        val nmeaMethods = listOf(
            "addNmeaListener",
            "registerGnssNmeaCallback"
        )
        nmeaMethods.forEach { methodName ->
            hookAll(clazz, methodName) { chain ->
                if (PreferencesUtil.getIsPlaying() == true) {
                    module.log(Log.INFO, tag, "Suppressed NMEA listener registration ($methodName) to prevent coordinate leak.")
                    defaultReturnValue(chain.executable as? Method) ?: true
                } else {
                    chain.proceed()
                }
            }
        }
    }

    /**
     * Hooks GNSS satellite status callbacks.
     */
    private fun hookGnssStatusCallbacks(clazz: Class<*>) {
        val gnssMethods = listOf(
            "registerGnssStatusCallback",
            "addGpsStatusListener"
        )
        gnssMethods.forEach { methodName ->
            hookAll(clazz, methodName) { chain ->
                if (PreferencesUtil.getIsPlaying() == true) {
                    module.log(Log.INFO, tag, "Suppressed GNSS status callback ($methodName) while spoofing.")
                    defaultReturnValue(chain.executable as? Method) ?: true
                } else {
                    chain.proceed()
                }
            }
        }
    }

    /**
     * Hooks raw GNSS satellite measurement callbacks so apps cannot calculate their own
     * hardware fix from pseudoranges and carrier phase.
     */
    private fun hookGnssMeasurements(clazz: Class<*>) {
        val measurementMethods = listOf(
            "registerGnssMeasurementsCallback",
            "registerGnssNavigationMessageCallback",
            "registerAntennaInfoListener"
        )
        measurementMethods.forEach { methodName ->
            hookAll(clazz, methodName) { chain ->
                if (PreferencesUtil.getIsPlaying() == true) {
                    module.log(Log.INFO, tag, "Suppressed raw GNSS measurement registration ($methodName).")
                    defaultReturnValue(chain.executable as? Method) ?: false
                } else {
                    chain.proceed()
                }
            }
        }
    }

    /**
     * Hooks the concrete [LocationListener] implementation class used by the target app,
     * traversing its class hierarchy to ensure methods defined in abstract or base listener classes
     * are also intercepted. Replaces any real [Location] or `List<Location>` passed into `onLocationChanged`.
     */
    private fun hookListenerClassIfNeeded(listenerClass: Class<*>) {
        var current: Class<*>? = listenerClass
        while (current != null && current != Any::class.java) {
            val targetClass = current
            if (hookedListenerClasses.add(targetClass)) {
                runCatching {
                    val methods = targetClass.declaredMethods.filter { it.name == "onLocationChanged" }
                    methods.forEach { method ->
                        module.hook(method).intercept { chain ->
                            if (PreferencesUtil.getIsPlaying() != true) {
                                return@intercept chain.proceed()
                            }

                            LocationUtil.updateLocation()
                            val args = chain.args.toTypedArray()
                            var modified = false

                            args.indices.forEach { i ->
                                val arg = args[i]
                                if (arg is Location) {
                                    args[i] = LocationUtil.createFakeLocation(originalLocation = arg, provider = arg.provider ?: LocationManager.GPS_PROVIDER)
                                    modified = true
                                } else if (arg is List<*>) {
                                    val fakeList = arg.map { item ->
                                        if (item is Location) {
                                            LocationUtil.createFakeLocation(originalLocation = item, provider = item.provider ?: LocationManager.GPS_PROVIDER)
                                        } else {
                                            item
                                        }
                                    }
                                    args[i] = fakeList
                                    modified = true
                                }
                            }

                            if (modified) {
                                chain.proceed(args)
                            } else {
                                chain.proceed()
                            }
                        }
                    }
                    if (methods.isNotEmpty()) {
                        module.log(Log.INFO, tag, "Hooked onLocationChanged on class: ${targetClass.name}")
                    }
                }.onFailure {
                    module.log(Log.ERROR, tag, "Failed hooking listener class ${targetClass.name}: ${it.message}")
                }
            }
            current = current.superclass
        }
    }

    /**
     * Global continuous heartbeat that pushes fresh fake locations to all currently registered
     * listeners every second, preventing domestic map SDKs from timing out or falling back to
     * network positioning.
     */
    private fun startContinuousHeartbeatLoop() {
        scheduler.scheduleAtFixedRate({
            if (PreferencesUtil.getIsPlaying() == true) {
                LocationUtil.updateLocation()
                val fake = LocationUtil.createFakeLocation()
                val listeners = synchronized(registeredListeners) { registeredListeners.toList() }
                if (listeners.isNotEmpty()) {
                    mainHandler.post {
                        listeners.forEach { listener ->
                            runCatching { listener.onLocationChanged(fake) }
                        }
                    }
                }
            }
        }, 500, 1000, TimeUnit.MILLISECONDS)
    }

    /**
     * Starts a 1000ms periodic heartbeat that pushes fresh fake locations to the listener.
     */
    private fun startHeartbeat(listener: LocationListener) {
        stopHeartbeat(listener)
        val future = scheduler.scheduleAtFixedRate({
            if (PreferencesUtil.getIsPlaying() == true) {
                LocationUtil.updateLocation()
                val fake = LocationUtil.createFakeLocation()
                mainHandler.post {
                    runCatching { listener.onLocationChanged(fake) }
                }
            }
        }, 500, 1000, TimeUnit.MILLISECONDS)
        activeTickers[listener] = future
    }

    private fun stopHeartbeat(listener: Any) {
        activeTickers.remove(listener)?.cancel(false)
    }

    private fun hookAll(clazz: Class<*>, methodName: String, hooker: Hooker) {
        val methods = clazz.declaredMethods.filter { it.name == methodName }
        if (methods.isEmpty()) return

        var count = 0
        methods.forEach { method ->
            runCatching {
                module.hook(method).intercept(hooker)
                count++
            }.onFailure { module.log(Log.ERROR, tag, "Failed hooking ${clazz.name}#$methodName: ${it.message}") }
        }
        if (count > 0) {
            module.log(Log.INFO, tag, "Hooked ${clazz.name}#$methodName ($count overloads)")
        }
    }

    private fun defaultReturnValue(method: Method?): Any? {
        return when (method?.returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0F
            java.lang.Double.TYPE -> 0.0
            else -> null
        }
    }
}
