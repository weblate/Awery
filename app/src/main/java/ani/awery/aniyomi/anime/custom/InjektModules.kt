package ani.awery.aniyomi.anime.custom


import android.app.Application
import android.content.Context
import androidx.core.content.ContextCompat
import ani.awery.download.DownloadsManager
import ani.awery.media.manga.MangaCache
import ani.awery.parsers.novel.NovelExtensionManager
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.core.preference.AndroidPreferenceStore
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.source.anime.AndroidAnimeSourceManager
import eu.kanade.tachiyomi.source.manga.AndroidMangaSourceManager
import kotlinx.serialization.json.Json
import tachiyomi.core.preference.PreferenceStore
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class AppModule(val app: Application) : InjektModule {
    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)

        addSingletonFactory { DownloadsManager(app) }

        addSingletonFactory { NetworkHelper(app, get()) }

        addSingletonFactory { AnimeExtensionManager(app) }
        addSingletonFactory { MangaExtensionManager(app) }
        addSingletonFactory { NovelExtensionManager(app) }

        addSingletonFactory<AnimeSourceManager> { AndroidAnimeSourceManager(app, get()) }
        addSingletonFactory<MangaSourceManager> { AndroidMangaSourceManager(app, get()) }

        val sharedPreferences = app.getSharedPreferences("Awery", Context.MODE_PRIVATE)
        addSingleton(sharedPreferences)

        addSingletonFactory {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }

        addSingletonFactory { MangaCache() }

        ContextCompat.getMainExecutor(app).execute {
            get<AnimeSourceManager>()
            get<MangaSourceManager>()
        }
    }
}

class PreferenceModule(val application: Application) : InjektModule {
    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<PreferenceStore> {
            AndroidPreferenceStore(application)
        }

        addSingletonFactory {
            NetworkPreferences(
                preferenceStore = get(),
                verboseLogging = false,
            )
        }

        addSingletonFactory {
            SourcePreferences(get())
        }

        addSingletonFactory {
            BasePreferences(application, get())
        }
    }
}