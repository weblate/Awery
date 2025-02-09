package ani.awery

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import ani.awery.aniyomi.anime.custom.AppModule
import ani.awery.aniyomi.anime.custom.PreferenceModule
import ani.awery.parsers.AnimeSources
import ani.awery.parsers.MangaSources
import ani.awery.parsers.NovelSources
import ani.awery.parsers.novel.NovelExtensionManager
import com.google.android.material.color.DynamicColors
import com.mrboomdev.awery.AweryApp
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger
import tachiyomi.core.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@SuppressLint("StaticFieldLeak")
open class App : Application() {
    private lateinit var animeExtensionManager: AnimeExtensionManager
    private lateinit var mangaExtensionManager: MangaExtensionManager
    private lateinit var novelExtensionManager: NovelExtensionManager

    init {
        instance = this
    }

    val mFTActivityLifecycleCallbacks = FTActivityLifecycleCallbacks()

    override fun onCreate() {
        super.onCreate()
        val sharedPreferences = getSharedPreferences("Awery", Context.MODE_PRIVATE)
        val useMaterialYou = sharedPreferences.getBoolean("use_material_you", false)

        if(useMaterialYou) {
            DynamicColors.applyToActivitiesIfAvailable(this)
            //TODO: HarmonizedColors
        }

        registerActivityLifecycleCallbacks(mFTActivityLifecycleCallbacks)

        if(AweryApp.USE_KT_APP_INIT) {
            Injekt.importModule(AppModule(this))
            Injekt.importModule(PreferenceModule(this))

            initializeNetwork(baseContext)

            setupNotificationChannels()
            if(!LogcatLogger.isInstalled) {
                LogcatLogger.install(AndroidLogcatLogger(LogPriority.VERBOSE))
            }

            animeExtensionManager = Injekt.get()
            mangaExtensionManager = Injekt.get()
            novelExtensionManager = Injekt.get()

            val animeScope = CoroutineScope(Dispatchers.Default)
            animeScope.launch {
                animeExtensionManager.findAvailableExtensions()
                AnimeSources.init(animeExtensionManager.installedExtensionsFlow)
            }

            val mangaScope = CoroutineScope(Dispatchers.Default)
            mangaScope.launch {
                mangaExtensionManager.findAvailableExtensions()
                MangaSources.init(mangaExtensionManager.installedExtensionsFlow)
            }

            val novelScope = CoroutineScope(Dispatchers.Default)
            novelScope.launch {
                novelExtensionManager.findAvailableExtensions()
                NovelSources.init(novelExtensionManager.installedExtensionsFlow)
            }
        }
    }

    private fun setupNotificationChannels() {
        try {
            Notifications.createChannels(this)
        } catch(e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to modify notification channels" }
        }
    }

    inner class FTActivityLifecycleCallbacks : ActivityLifecycleCallbacks {
        var currentActivity: Activity? = null
        override fun onActivityCreated(p0: Activity, p1: Bundle?) {}
        override fun onActivityStarted(p0: Activity) {
            currentActivity = p0
        }

        override fun onActivityResumed(p0: Activity) {
            currentActivity = p0
        }

        override fun onActivityPaused(p0: Activity) {}
        override fun onActivityStopped(p0: Activity) {}
        override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {}
        override fun onActivityDestroyed(p0: Activity) {}
    }

    companion object {
        private var instance: App? = null
        var context: Context? = null

        fun currentContext(): Context? {
            return instance?.mFTActivityLifecycleCallbacks?.currentActivity ?: context
        }

        fun currentActivity(): Activity? {
            return instance?.mFTActivityLifecycleCallbacks?.currentActivity
        }
    }
}