package ani.awery.parsers

import ani.awery.Lazier
import ani.awery.logger
import ani.awery.media.Media
import ani.awery.media.anime.Episode
import ani.awery.media.manga.MangaChapter
import ani.awery.tryWithSuspend
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.source.model.SManga

abstract class WatchSources : BaseSources() {

    override operator fun get(i: Int): AnimeParser {
        return (list.getOrNull(i) ?: list.firstOrNull())?.get?.value as? AnimeParser
            ?: EmptyAnimeParser()
    }


    suspend fun loadEpisodesFromMedia(i: Int, media: Media): MutableMap<String, Episode> {
        return tryWithSuspend(true) {
            val res = get(i).autoSearch(media) ?: return@tryWithSuspend mutableMapOf()
            loadEpisodes(i, res.link, res.extra, res.sAnime)
        } ?: mutableMapOf()
    }

    suspend fun loadEpisodes(
        i: Int,
        showLink: String,
        extra: Map<String, String>?,
        sAnime: SAnime?
    ): MutableMap<String, Episode> {
        val map = mutableMapOf<String, Episode>()
        val parser = get(i)
        tryWithSuspend(true) {
            if (sAnime != null) {
                parser.loadEpisodes(showLink, extra, sAnime).forEach {
                    map[it.number] = Episode(
                        it.number,
                        it.link,
                        it.title,
                        it.description,
                        it.thumbnail,
                        it.isFiller,
                        extra = it.extra,
                        sEpisode = it.sEpisode
                    )
                }
            }
        }
        return map
    }

}

abstract class MangaReadSources : BaseSources() {

    override operator fun get(i: Int): MangaParser {
        return (list.getOrNull(i) ?: list.firstOrNull())?.get?.value as? MangaParser
            ?: EmptyMangaParser()
    }

    suspend fun loadChaptersFromMedia(i: Int, media: Media): MutableMap<String, MangaChapter> {
        return tryWithSuspend(true) {
            val res = get(i).autoSearch(media) ?: return@tryWithSuspend mutableMapOf()
            loadChapters(i, res)
        } ?: mutableMapOf()
    }

    suspend fun loadChapters(i: Int, show: ShowResponse): MutableMap<String, MangaChapter> {
        val map = mutableMapOf<String, MangaChapter>()
        val parser = get(i)

        show.sManga?.let { sManga ->
            tryWithSuspend(true) {
                parser.loadChapters(show.link, show.extra, sManga).forEach {
                    map[it.number] = MangaChapter(it)
                }
            }
        }
        //must be downloaded
        if (show.sManga == null) {
            logger("sManga is null")
        }
        if (parser is OfflineMangaParser && show.sManga == null) {
            tryWithSuspend(true) {
                // Since we've checked, we can safely cast parser to OfflineMangaParser and call its methods
                parser.loadChapters(show.link, show.extra, SManga.create()).forEach {
                    map[it.number] = MangaChapter(it)
                }
            }
        } else {
            logger("Parser is not an instance of OfflineMangaParser")
        }


        logger("map size ${map.size}")
        return map
    }
}

abstract class NovelReadSources : BaseSources() {
    override operator fun get(i: Int): NovelParser? {
        return if (list.isNotEmpty()) {
            (list.getOrNull(i) ?: list[0]).get.value as NovelParser
        } else {
            return EmptyNovelParser()
        }
    }

}

class EmptyNovelParser : NovelParser() {

    override val volumeRegex: Regex = Regex("")

    override suspend fun loadBook(link: String, extra: Map<String, String>?): Book {
        return Book("", "", null, emptyList())  // Return an empty Book object or some default value
    }

    override suspend fun search(query: String): List<ShowResponse> {
        return listOf() // Return an empty list or some default value
    }
}

abstract class BaseSources {
    abstract val list: List<Lazier<BaseParser>>

    val names: List<String> get() = list.map { it.name }

    fun flushText() {
        list.forEach {
            if (it.get.isInitialized())
                it.get.value?.showUserText = ""
        }
    }

    open operator fun get(i: Int): BaseParser? {
        return list[i].get.value
    }

    fun saveResponse(i: Int, mediaId: Int, response: ShowResponse) {
        get(i)?.saveShowResponse(mediaId, response, true)
    }
}