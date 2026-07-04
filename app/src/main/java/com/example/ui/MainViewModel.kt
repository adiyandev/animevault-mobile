package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.RetrofitInstance
import com.example.data.api.VideasyShow
import com.example.data.model.Anime
import com.example.data.model.Genre
import com.example.data.model.Images
import com.example.data.model.ImageUrls
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.example.data.api.TmdbApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

sealed class HomeState {
    object Loading : HomeState()
    data class Success(
        val trending: List<Anime>,
        val popular: List<Anime>,
        val upcoming: List<Anime>,
        val seasonal: List<Anime>
    ) : HomeState()
    data class Error(val message: String) : HomeState()
}

sealed class DetailState {
    object Loading : DetailState()
    data class Success(val anime: Anime) : DetailState()
    data class Error(val message: String) : DetailState()
}

// User Profile Data Class
data class User(
    val username: String,
    val email: String,
    val avatarUrl: String = "https://api.dicebear.com/7.x/adventurer/svg?seed=AnimeFan",
    val bannerUrl: String = "",
    val level: Int = 1,
    val xp: Int = 15,
    val xpNeeded: Int = 100,
    val totalWatchTime: Int = 48 // minutes
)

// Airing Schedule Data Class
data class AiringItem(
    val id: Int,
    val animeId: Int,
    val title: String,
    val imageUrl: String,
    val episode: Int,
    val airingTime: String,
    val countdown: String,
    val rating: Double,
    val dayOfWeek: Int, // 0=Sunday, 1=Monday, 2=Tuesday, etc.
    val airingAt: Long = 0L
)

fun getUtcTimestampForDayAndTime(dayOfWeek: Int, timeStr: String): Long {
    val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    // Align to Sunday of current week
    calendar.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SUNDAY)
    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
    calendar.set(java.util.Calendar.MINUTE, 0)
    calendar.set(java.util.Calendar.SECOND, 0)
    calendar.set(java.util.Calendar.MILLISECOND, 0)
    
    calendar.add(java.util.Calendar.DAY_OF_WEEK, dayOfWeek)
    
    val timeParts = timeStr.split(" ")
    val hms = timeParts[0].split(":")
    val hour = hms[0].toIntOrNull() ?: 12
    val minute = if (hms.size > 1) hms[1].toIntOrNull() ?: 0 else 0
    val isPm = timeParts.getOrNull(1)?.equals("PM", ignoreCase = true) ?: false
    
    calendar.set(java.util.Calendar.HOUR_OF_DAY, if (isPm) { if (hour == 12) 12 else hour + 12 } else { if (hour == 12) 0 else hour })
    calendar.set(java.util.Calendar.MINUTE, minute)
    
    return calendar.timeInMillis / 1000L
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        internal fun mapVideasyShowToAnime(
            show: VideasyShow,
            offset: Int,
            fallbackEpisodes: Int,
            fallbackStatus: String,
            fallbackGenre: String,
            mediaType: String = "tv"
        ): Anime {
            val score = show.rating?.average ?: 7.5
            val yearStr = show.premiered?.split("-")?.firstOrNull()
            val year = yearStr?.toIntOrNull() ?: 2020
            val cleanSynopsis = show.summary?.replace(Regex("<[^>]*>"), "") ?: ""
            val title = show.name.trim().ifBlank { "Untitled title" }
            val imageUrl = show.image?.original ?: show.image?.medium ?: ""

            return Anime(
                malId = show.id + offset,
                title = title,
                titleEnglish = title,
                images = createAnimeImages(imageUrl),
                score = score,
                synopsis = cleanSynopsis,
                episodes = fallbackEpisodes,
                status = show.status?.takeIf { it.isNotBlank() } ?: fallbackStatus,
                year = year,
                genres = show.genres?.map { Genre(it) } ?: listOf(Genre(fallbackGenre)),
                mediaType = mediaType
            )
        }

        private fun createAnimeImages(url: String): Images {
            return Images(
                jpg = ImageUrls(imageUrl = url, largeImageUrl = url),
                webp = ImageUrls(imageUrl = url, largeImageUrl = url)
            )
        }
    }

    private val api = RetrofitInstance.api

    private val _homeState = MutableStateFlow<HomeState>(HomeState.Loading)
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()

    private val _detailState = MutableStateFlow<DetailState>(DetailState.Loading)
    val detailState: StateFlow<DetailState> = _detailState.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _favorites = MutableStateFlow<List<Anime>>(emptyList())
    val favorites: StateFlow<List<Anime>> = _favorites.asStateFlow()

    private val _continueWatching = MutableStateFlow<List<Pair<Anime, Int>>>(emptyList())
    val continueWatching: StateFlow<List<Pair<Anime, Int>>> = _continueWatching.asStateFlow()

    private val _reminders = MutableStateFlow<Set<Int>>(emptySet())
    val reminders: StateFlow<Set<Int>> = _reminders.asStateFlow()

    private val prefs by lazy {
        application.getSharedPreferences("animevault_prefs", Context.MODE_PRIVATE)
    }

    private val reminderPrefsKey = "reminder_ids"
    private val profileUsernameKey = "profile_username"
    private val profileEmailKey = "profile_email"
    private val profileAvatarKey = "profile_avatar"
    private val profileBannerKey = "profile_banner"
    private val profileLevelKey = "profile_level"
    private val profileXpKey = "profile_xp"
    private val profileXpNeededKey = "profile_xp_needed"
    private val profileWatchTimeKey = "profile_watch_time"

    init {
        loadCurrentUserFromPrefs()
        loadRemindersFromPrefs()
    }

    private fun saveCurrentUserToPrefs(user: User) {
        prefs.edit()
            .putString(profileUsernameKey, user.username)
            .putString(profileEmailKey, user.email)
            .putString(profileAvatarKey, user.avatarUrl)
            .putString(profileBannerKey, user.bannerUrl)
            .putInt(profileLevelKey, user.level)
            .putInt(profileXpKey, user.xp)
            .putInt(profileXpNeededKey, user.xpNeeded)
            .putInt(profileWatchTimeKey, user.totalWatchTime)
            .apply()
    }

    private fun loadCurrentUserFromPrefs() {
        val username = prefs.getString(profileUsernameKey, null)
        val email = prefs.getString(profileEmailKey, null)
        if (!username.isNullOrBlank() && !email.isNullOrBlank()) {
            val avatar = prefs.getString(profileAvatarKey, "https://api.dicebear.com/7.x/adventurer/svg?seed=AnimeFan") ?: "https://api.dicebear.com/7.x/adventurer/svg?seed=AnimeFan"
            val banner = prefs.getString(profileBannerKey, "") ?: ""
            val level = prefs.getInt(profileLevelKey, 1)
            val xp = prefs.getInt(profileXpKey, 15)
            val xpNeeded = prefs.getInt(profileXpNeededKey, 100)
            val watchTime = prefs.getInt(profileWatchTimeKey, 48)
            _currentUser.value = User(username, email, avatar, banner, level, xp, xpNeeded, watchTime)
        }
    }

    private fun clearCurrentUserPrefs() {
        prefs.edit()
            .remove(profileUsernameKey)
            .remove(profileEmailKey)
            .remove(profileAvatarKey)
            .remove(profileBannerKey)
            .remove(profileLevelKey)
            .remove(profileXpKey)
            .remove(profileXpNeededKey)
            .remove(profileWatchTimeKey)
            .apply()
    }

    private val _moviesList = MutableStateFlow<List<Anime>>(emptyList())
    val moviesList: StateFlow<List<Anime>> = _moviesList.asStateFlow()

    private val _tvShowsList = MutableStateFlow<List<Anime>>(emptyList())
    val tvShowsList: StateFlow<List<Anime>> = _tvShowsList.asStateFlow()

    private val _recommendations = MutableStateFlow<List<Anime>>(emptyList())
    val recommendations: StateFlow<List<Anime>> = _recommendations.asStateFlow()

    private val _airingSchedule = MutableStateFlow<List<AiringItem>>(emptyList())
    val airingSchedule: StateFlow<List<AiringItem>> = _airingSchedule.asStateFlow()

    private val _rawSchedule = MutableStateFlow<List<AiringItem>>(emptyList())
    val rawSchedule: StateFlow<List<AiringItem>> = _rawSchedule.asStateFlow()

    private val _scheduleByDay = MutableStateFlow<Map<Int, List<AiringItem>>>(emptyMap())
    val scheduleByDay: StateFlow<Map<Int, List<AiringItem>>> = _scheduleByDay.asStateFlow()

    val tmdbIds = java.util.concurrent.ConcurrentHashMap<Int, String>()
    val videasyIds = java.util.concurrent.ConcurrentHashMap<Int, String>()

    fun getTmdbId(animeId: Int): String? {
        return tmdbIds[animeId]
    }

    fun getVideasyId(animeId: Int): String? {
        return videasyIds[animeId]
    }

    private suspend fun resolveTmdbId(show: VideasyShow, preferMovie: Boolean? = null): String? {
        // Hardcoded TMDb API key (inlined per user request)
        val apiKey = "288d312680f3117dd4c56964be6809dc"

        // 1) Try using IMDb external id if present
        val imdbId = show.externals?.imdb?.takeIf { it.isNotBlank() }

        // Build a local TMDb API instance to avoid relying on RetrofitInstance visibility during compilation
        val tmdb = try {
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val client = OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                .build()
            Retrofit.Builder()
                .baseUrl("https://api.themoviedb.org/3/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(TmdbApi::class.java)
        } catch (e: Throwable) {
            android.util.Log.w("TmdbResolver", "Failed building tmdb client: ${e.message}")
            return null
        }
        if (!imdbId.isNullOrBlank()) {
            try {
                val resp = tmdb.findByExternalId(imdbId, apiKey, "imdb_id")
                val movieFirst = resp.movieResults?.firstOrNull()
                if (movieFirst?.id != null) return movieFirst.id.toString()
                val tvFirst = resp.tvResults?.firstOrNull()
                if (tvFirst?.id != null) return tvFirst.id.toString()
            } catch (e: Exception) {
                android.util.Log.w("TmdbResolver", "Failed to resolve TMDB via IMDb id $imdbId: ${e.message}")
            }
        }

        // 2) Fallback to searching by title (optionally prefer movie or tv)
        val title = show.name.takeIf { it.isNotBlank() } ?: return null
        val year = show.premiered?.split("-")?.firstOrNull()?.toIntOrNull()

        try {
            if (preferMovie == true) {
                val resp = tmdb.searchMovies(apiKey, title, year)
                val first = resp.results?.firstOrNull()
                if (first?.id != null) return first.id.toString()
                return null
            }

            if (preferMovie == false) {
                val resp = tmdb.searchTv(apiKey, title, year)
                val first = resp.results?.firstOrNull()
                if (first?.id != null) return first.id.toString()
                return null
            }

            val movieResp = tmdb.searchMovies(apiKey, title, year)
            val mfirst = movieResp.results?.firstOrNull()
            if (mfirst?.id != null) return mfirst.id.toString()

            val tvResp = tmdb.searchTv(apiKey, title, year)
            val tfirst = tvResp.results?.firstOrNull()
            if (tfirst?.id != null) return tfirst.id.toString()

            return null
        } catch (e: Exception) {
            android.util.Log.w("TmdbResolver", "Fallback TMDB query failed for ${show.name}: ${e.message}")
            return null
        }
    }

    private fun cacheTmdbId(animeId: Int, tmdbId: String?) {
        if (!tmdbId.isNullOrBlank()) {
            tmdbIds[animeId] = tmdbId
        }
    }

    private fun cacheVideasyId(animeId: Int, videasyId: String?) {
        if (!videasyId.isNullOrBlank()) {
            videasyIds[animeId] = videasyId
        }
    }

    private var currentTrending = listOf<Anime>()
    private var currentPopular = listOf<Anime>()
    private var currentUpcoming = listOf<Anime>()
    private var currentSeasonal = listOf<Anime>()

    // Beautiful Curated Local Fallback Database of Legendary Anime to guarantee instant high-fidelity loading!
    val localTrendingAnime = listOf(
        Anime(7, "Death Note", "Death Note", createAnimeImages("https://cdn.pixabay.com/photo/2020/05/20/19/27/death-note-5202159_1280.jpg"), 8.6, "A student discovers a notebook that can kill anyone whose name is written in it.", 37, "Finished Airing", 2006, listOf(Genre("Mystery"), Genre("Psychological"))),
        Anime(20, "Monster", "Monster", createAnimeImages("https://images.unsplash.com/photo-1519337265831-281ec6cc8514?auto=format&fit=crop&w=400&q=80"), 9.0, "A brilliant neurosurgeon saves a boy and becomes entangled in a dark conspiracy as he hunts for the boy he once saved.", 74, "Finished Airing", 2004, listOf(Genre("Mystery"), Genre("Psychological"), Genre("Thriller"))),
        Anime(21, "Classroom of the Elite", "Classroom of the Elite", createAnimeImages("https://images.unsplash.com/photo-1503676260728-1c00da094a0b?auto=format&fit=crop&w=400&q=80"), 7.6, "A school where only the best advance, and students scheme for status in a ruthless meritocracy.", 12, "Finished Airing", 2017, listOf(Genre("Drama"), Genre("School"), Genre("Psychological"))),
        Anime(22, "Tonikawa: Over the Moon For You", "Tonikawa", createAnimeImages("https://images.unsplash.com/photo-1529336953128-8a08e5a1d7b6?auto=format&fit=crop&w=400&q=80"), 7.8, "A newly married couple navigate life, love, and the comedic trials of married life after a fateful meeting.", 12, "Finished Airing", 2020, listOf(Genre("Romance"), Genre("Comedy")))
    )

    val localPopularAnime = listOf(
        Anime(6, "Fullmetal Alchemist: Brotherhood", "Fullmetal Alchemist", createAnimeImages("https://cdn.pixabay.com/photo/2016/11/29/08/42/brothers-1868500_1280.jpg"), 9.2, "Two brothers use alchemy in an attempt to bring their mother back to life.", 64, "Finished Airing", 2009, listOf(Genre("Drama"), Genre("Fantasy"))),
        Anime(7, "Death Note", "Death Note", createAnimeImages("https://cdn.pixabay.com/photo/2020/05/20/19/27/death-note-5202159_1280.jpg"), 8.6, "A student discovers a notebook that can kill anyone whose name is written in it.", 37, "Finished Airing", 2006, listOf(Genre("Mystery"), Genre("Psychological"))),
        Anime(8, "Hunter x Hunter (2011)", "Hunter x Hunter", createAnimeImages("https://cdn.pixabay.com/photo/2020/06/15/15/22/hunter-x-hunter-5302220_1280.jpg"), 9.0, "Gon Freecss seeks to become a Legendary Hunter to find his long-lost father.", 148, "Finished Airing", 2011, listOf(Genre("Adventure"), Genre("Action"))),
        Anime(9, "My Hero Academia", "My Hero Academia", createAnimeImages("https://cdn.pixabay.com/photo/2021/04/06/20/02/midoriya-izuku-6157345_1280.jpg"), 8.0, "In a world of superheroes, a boy born without quirks trains to inherit powers.", 138, "Currently Airing", 2016, listOf(Genre("Action"), Genre("School")))
    )

    val localUpcomingAnime = listOf(
        Anime(10, "Chainsaw Man Movie: Reze-hen", "Chainsaw Man Movie", createAnimeImages("https://cdn.pixabay.com/photo/2023/04/09/20/01/anime-7912235_1280.jpg"), 8.9, "Denji encounters a mysterious girl named Reze in a cafe.", 1, "Not Yet Aired", 2026, listOf(Genre("Action"), Genre("Romance"))),
        Anime(11, "Bleach: Thousand-Year Blood War Part 3", "Bleach TYBW Part 3", createAnimeImages("https://cdn.pixabay.com/photo/2022/10/24/09/20/ichigo-7543048_1280.jpg"), 9.0, "Ichigo and his allies fight to save the Soul Society from the Quincy empire.", 13, "Not Yet Aired", 2026, listOf(Genre("Action"), Genre("Fantasy")))
    )

    val localSeasonalAnime = listOf(
        Anime(12, "Frieren: Beyond Journey's End", "Frieren", createAnimeImages("https://cdn.pixabay.com/photo/2024/02/25/11/40/frieren-8595679_1280.jpg"), 9.3, "An elf mage re-evaluates her relationships with mortals long after defeating the Demon King.", 28, "Currently Airing", 2024, listOf(Genre("Adventure"), Genre("Fantasy"))),
        Anime(13, "Spy x Family Season 2", "Spy x Family", createAnimeImages("https://cdn.pixabay.com/photo/2022/05/18/14/40/anya-forger-7205435_1280.jpg"), 8.4, "A spy, an assassin, and a telepath form a fake family to keep world peace.", 12, "Finished Airing", 2023, listOf(Genre("Comedy"), Genre("Action")))
    )

    val localMovies = listOf(
        Anime(1001, "Interstellar", "Interstellar", createAnimeImages("https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=400&q=80"), 8.7, "A team of explorers travel through a wormhole in space in an attempt to ensure humanity's survival.", 1, "Movie", 2014, listOf(Genre("Sci-Fi"), Genre("Adventure"), Genre("Drama")), mediaType = "movie"),
        Anime(1002, "The Dark Knight", "The Dark Knight", createAnimeImages("https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?auto=format&fit=crop&w=400&q=80"), 9.0, "When the menace known as the Joker wreaks havoc and chaos on the people of Gotham, Batman must accept one of the greatest psychological and physical tests of his ability to fight injustice.", 1, "Movie", 2008, listOf(Genre("Action"), Genre("Crime"), Genre("Drama")), mediaType = "movie"),
        Anime(1003, "Oppenheimer", "Oppenheimer", createAnimeImages("https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?auto=format&fit=crop&w=400&q=80"), 8.4, "The story of American scientist J. Robert Oppenheimer and his role in the development of the atomic bomb.", 1, "Movie", 2023, listOf(Genre("Biography"), Genre("Drama"), Genre("History")), mediaType = "movie"),
        Anime(1004, "Inception", "Inception", createAnimeImages("https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&w=400&q=80"), 8.8, "A thief who steals corporate secrets through the use of dream-sharing technology is given the inverse task of planting an idea into the mind of a C.E.O.", 1, "Movie", 2010, listOf(Genre("Sci-Fi"), Genre("Action"), Genre("Adventure")), mediaType = "movie"),
        Anime(1005, "Spirited Away", "Spirited Away", createAnimeImages("https://cdn.pixabay.com/photo/2020/05/20/19/27/spirited-away-5202159_1280.jpg"), 8.9, "A young girl wanders into a world ruled by gods, witches, and spirits.", 1, "Movie", 2001, listOf(Genre("Adventure"), Genre("Fantasy")), mediaType = "movie"),
        Anime(1006, "Your Name.", "Your Name.", createAnimeImages("https://cdn.pixabay.com/photo/2020/05/01/16/00/anime-5117769_1280.jpg"), 8.8, "Two high school students swap bodies and must find a way to meet.", 1, "Movie", 2016, listOf(Genre("Romance"), Genre("Drama")), mediaType = "movie"),
        Anime(1007, "Parasite", "Parasite", createAnimeImages("https://images.unsplash.com/photo-1536440136628-849c177e76a1?auto=format&fit=crop&w=400&q=80"), 8.6, "Greed and class discrimination threaten the newly formed symbiotic relationship between the wealthy Park family and the destitute Kim clan.", 1, "Movie", 2019, listOf(Genre("Thriller"), Genre("Drama")), mediaType = "movie")
    )

    val localTvShows = listOf(
        Anime(2001, "Breaking Bad", "Breaking Bad", createAnimeImages("https://images.unsplash.com/photo-1560169897-fc0cdbdfa4d5?auto=format&fit=crop&w=400&q=80"), 9.5, "A chemistry teacher diagnosed with inoperable lung cancer turns to manufacturing and selling methamphetamine with a former student in order to secure his family's future.", 62, "TV", 2008, listOf(Genre("Crime"), Genre("Drama"), Genre("Thriller")), mediaType = "tv"),
        Anime(2002, "Stranger Things", "Stranger Things", createAnimeImages("https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&w=400&q=80"), 8.7, "When a young boy vanishes, a small town uncovers a mystery involving secret experiments, terrifying supernatural forces and one strange little girl.", 42, "TV", 2016, listOf(Genre("Sci-Fi"), Genre("Horror"), Genre("Drama")), mediaType = "tv"),
        Anime(2003, "Squid Game", "Squid Game", createAnimeImages("https://images.unsplash.com/photo-1627856013091-fed6e4e30025?auto=format&fit=crop&w=400&q=80"), 8.0, "Hundreds of cash-strapped players accept a strange invitation to compete in children's games. Inside, a tempting prize awaits with deadly high stakes.", 9, "TV", 2021, listOf(Genre("Thriller"), Genre("Drama"), Genre("Action")), mediaType = "tv"),
        Anime(2004, "Crash Landing on You", "Crash Landing on You", createAnimeImages("https://images.unsplash.com/photo-1492691527719-9d1e07e534b4?auto=format&fit=crop&w=400&q=80"), 8.7, "The absolute top-secret love story of a chaebol heiress who made a forced landing in North Korea because of a paragliding accident and a North Korean special officer.", 16, "TV", 2019, listOf(Genre("Romance"), Genre("Comedy"), Genre("Drama")), mediaType = "tv"),
        Anime(2005, "Queen of Tears", "Queen of Tears", createAnimeImages("https://images.unsplash.com/photo-1518199266791-5375a83190b7?auto=format&fit=crop&w=400&q=80"), 8.4, "The queen of department stores and her small-town husband weather a marital crisis until love miraculously begins to bloom again.", 16, "TV", 2024, listOf(Genre("Romance"), Genre("Drama"), Genre("Comedy")), mediaType = "tv"),
        Anime(2006, "The Last of Us", "The Last of Us", createAnimeImages("https://images.unsplash.com/photo-1550745165-9bc0b252726f?auto=format&fit=crop&w=400&q=80"), 8.8, "After a global pandemic destroys civilization, a hardened survivor takes charge of a 14-year-old girl who may be humanity's last hope.", 9, "TV", 2023, listOf(Genre("Action"), Genre("Drama"), Genre("Sci-Fi")), mediaType = "tv"),
        Anime(2007, "Goblin: The Lonely and Great God", "Goblin (Guardian)", createAnimeImages("https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?auto=format&fit=crop&w=400&q=80"), 8.6, "In his quest for a bride to break his immortal curse, a 939-year-old guardian of souls meets a cheerful grim reaper and a sprightly student.", 16, "TV", 2016, listOf(Genre("Fantasy"), Genre("Romance"), Genre("Drama")), mediaType = "tv")
    )

    val localAiringSchedule = listOf(
        AiringItem(1, 12, "Frieren: Beyond Journey's End", "https://cdn.pixabay.com/photo/2024/02/25/11/40/frieren-8595679_1280.jpg", 28, "10:30 PM", "Airs in 3h 12m", 9.3, 5, getUtcTimestampForDayAndTime(5, "10:30 PM")), // Friday
        AiringItem(2, 3, "Solo Leveling", "https://cdn.pixabay.com/photo/2024/01/29/18/14/manhwa-8540540_1280.jpg", 12, "11:00 PM", "Airs in 5h 45m", 8.5, 6, getUtcTimestampForDayAndTime(6, "11:00 PM")), // Saturday
        AiringItem(3, 9, "My Hero Academia Season 7", "https://cdn.pixabay.com/photo/2021/04/06/20/02/midoriya-izuku-6157345_1280.jpg", 8, "05:30 PM", "Aired 2h ago", 8.0, 6, getUtcTimestampForDayAndTime(6, "05:30 PM")), // Saturday
        AiringItem(4, 1, "Demon Slayer: Hasira Training Arc", "https://cdn.pixabay.com/photo/2020/09/25/11/30/demon-slayer-5601140_1280.jpg", 5, "11:15 PM", "Airs in 1d 4h", 8.7, 0, getUtcTimestampForDayAndTime(0, "11:15 PM")), // Sunday
        AiringItem(5, 4, "Jujutsu Kaisen Season 2", "https://cdn.pixabay.com/photo/2021/06/18/11/22/jujutsu-kaisen-6345842_1280.jpg", 23, "11:56 PM", "Airs in 2d 12h", 8.8, 4, getUtcTimestampForDayAndTime(4, "11:56 PM")), // Thursday
        AiringItem(6, 18, "Oshi no Ko Season 2", "https://cdn.pixabay.com/photo/2023/11/14/09/20/ai-hoshino-8387258_1280.jpg", 1, "09:00 PM", "Airs in 3d 5h", 8.5, 3, getUtcTimestampForDayAndTime(3, "09:00 PM")), // Wednesday
        AiringItem(7, 5, "Chainsaw Man", "https://cdn.pixabay.com/photo/2023/04/09/20/01/anime-7912235_1280.jpg", 12, "08:30 PM", "Airs in 4d 2h", 8.6, 1, getUtcTimestampForDayAndTime(1, "08:30 PM")), // Monday
        AiringItem(8, 2, "Attack on Titan Final Chapters", "https://cdn.pixabay.com/photo/2021/08/21/20/46/attack-on-titan-6563604_1280.jpg", 4, "10:00 PM", "Airs in 5d 1h", 9.1, 2, getUtcTimestampForDayAndTime(2, "10:00 PM"))  // Tuesday
    )

    init {
        updateScheduleStates(localAiringSchedule)
        // Ensure the AniList AiringSchedule query parameters are correctly mapping the current time range
        getAniListAiringScheduleQueryParams()
        loadTrending()
        loadMoviesAndTvShows()
        loadSchedule()
    }

    private fun updateScheduleStates(items: List<AiringItem>) {
        val localCalendar = java.util.Calendar.getInstance()
        val timezoneCorrectedItems = items.map { item ->
            if (item.airingAt > 0L) {
                localCalendar.timeInMillis = item.airingAt * 1000L
                val localDay = localCalendar.get(java.util.Calendar.DAY_OF_WEEK) - 1 // 0-indexed (Sunday = 0, Monday = 1, etc.)
                item.copy(dayOfWeek = localDay)
            } else {
                item
            }
        }
        
        _rawSchedule.value = timezoneCorrectedItems
        _airingSchedule.value = timezoneCorrectedItems
        
        // Transform rawSchedule into scheduleByDay with tracing/logging and UTC-to-Local conversion
        android.util.Log.d("ScheduleComponent", "TRANSFORMATION START: Mapping rawSchedule of size ${timezoneCorrectedItems.size} to scheduleByDay Map using dynamic local timezone calculation.")
        val grouped = (0..6).associateWith { day ->
            timezoneCorrectedItems.filter { it.dayOfWeek == day }
        }
        _scheduleByDay.value = grouped
        
        android.util.Log.d("ScheduleComponent", "TRANSFORMATION COMPLETE: scheduleByDay has ${grouped.keys.size} days filled.")
        for ((day, list) in grouped) {
            android.util.Log.d("ScheduleComponent", "  - Day $day contains ${list.size} items: ${list.joinToString { it.title }}")
        }
    }

    /**
     * Calculates the correct query parameters for the AniList AiringSchedule query,
     * mapping the current time range based on local system time.
     */
    fun getAniListAiringScheduleQueryParams(startOffsetDays: Int = 0, durationDays: Int = 7): Map<String, Any> {
        val nowMs = System.currentTimeMillis()
        val nowSec = nowMs / 1000
        
        // Align to the start of today (midnight) in local timezone
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        
        if (startOffsetDays != 0) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, startOffsetDays)
        }
        val startTimestamp = calendar.timeInMillis / 1000
        
        val endCalendar = calendar.clone() as java.util.Calendar
        endCalendar.add(java.util.Calendar.DAY_OF_YEAR, durationDays)
        val endTimestamp = endCalendar.timeInMillis / 1000
        
        val params = mapOf(
            "airingAt_greater" to startTimestamp.toInt(),
            "airingAt_lesser" to endTimestamp.toInt(),
            "page" to 1,
            "perPage" to 25
        )
        
        android.util.Log.d("ScheduleComponent", "AniList AiringSchedule Query Parameters Mapping:")
        android.util.Log.d("ScheduleComponent", "  - Current Time: ${java.util.Date(nowMs)} (seconds: $nowSec)")
        android.util.Log.d("ScheduleComponent", "  - airingAt_greater (Start of Range): $startTimestamp (${java.util.Date(startTimestamp * 1000)})")
        android.util.Log.d("ScheduleComponent", "  - airingAt_lesser (End of Range): $endTimestamp (${java.util.Date(endTimestamp * 1000)})")
        
        return params
    }

    fun loadSchedule() {
        viewModelScope.launch {
            val currentScheduleList = java.util.Collections.synchronizedList(localAiringSchedule.toMutableList())
            updateScheduleStates(currentScheduleList.toList())

            val days = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")

            for (dayIdx in days.indices) {
                val dayName = days[dayIdx]
                try {
                    val response = api.getSchedules(filter = dayName, limit = 15)
                    val mapped = response.data.filterSafe().mapIndexed { itemIdx, anime ->
                        AiringItem(
                            id = dayIdx * 100 + itemIdx + 1,
                            animeId = anime.malId,
                            title = anime.displayTitle,
                            imageUrl = anime.images?.webp?.largeImageUrl ?: anime.images?.jpg?.largeImageUrl ?: "",
                            episode = (anime.episodes ?: 12),
                            airingTime = "08:30 PM",
                            countdown = "Airs today",
                            rating = anime.score ?: 0.0,
                            dayOfWeek = dayIdx,
                            airingAt = getUtcTimestampForDayAndTime(dayIdx, "08:30 PM")
                        )
                    }
                    synchronized(currentScheduleList) {
                        currentScheduleList.removeAll { it.dayOfWeek == dayIdx }
                        currentScheduleList.addAll(mapped.ifEmpty {
                            localAiringSchedule.filter { it.dayOfWeek == dayIdx }
                        })
                        updateScheduleStates(currentScheduleList.toList())
                    }
                } catch (e: Exception) {
                    synchronized(currentScheduleList) {
                        currentScheduleList.removeAll { it.dayOfWeek == dayIdx }
                        currentScheduleList.addAll(localAiringSchedule.filter { it.dayOfWeek == dayIdx })
                        updateScheduleStates(currentScheduleList.toList())
                    }
                    android.util.Log.e("ScheduleComponent", "Failed to fetch schedule for $dayName: ${e.message}")
                }
                delay(300)
            }
        }
    }

    fun loadMoviesAndTvShows() {
        viewModelScope.launch {
            try {
                val videasy = RetrofitInstance.videasyApi

                val popularQueries = listOf(
                    "Breaking Bad", "Stranger Things", "The Last of Us", "Game of Thrones",
                    "Wednesday", "The Boys", "Squid Game", "Crash Landing on You",
                    "Queen of Tears", "Goblin", "Vincenzo", "Crash Course in Romance"
                )

                val fetchedShows = mutableListOf<Anime>()

                for (query in popularQueries) {
                    try {
                        val results = videasy.searchShows(query)
                        val match = results.firstOrNull()?.show
                        if (match != null && !isAnimationContent(match)) {
                            val mapped = mapVideasyShowToAnime(
                                show = match,
                                offset = 20000,
                                fallbackEpisodes = 12,
                                fallbackStatus = "Running",
                                fallbackGenre = "Drama",
                                mediaType = "tv"
                            )
                            if (fetchedShows.none { it.malId == mapped.malId }) {
                                fetchedShows.add(mapped)
                            }

                            cacheTmdbId(mapped.malId, resolveTmdbId(match, preferMovie = false))
                            cacheVideasyId(mapped.malId, match.id.toString())
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("VideasyAPI", "Show search failed for $query: ${e.message}")
                    }
                }

                try {
                    val discoverPage = videasy.getShows(0)
                    for (show in discoverPage) {
                        if (!isAnimationContent(show)) {
                            val mapped = mapVideasyShowToAnime(
                                show = show,
                                offset = 20000,
                                fallbackEpisodes = 12,
                                fallbackStatus = "Running",
                                fallbackGenre = "Drama",
                                mediaType = "tv"
                            )
                            if (fetchedShows.none { it.malId == mapped.malId }) {
                                fetchedShows.add(mapped)
                            }

                            cacheTmdbId(mapped.malId, resolveTmdbId(show, preferMovie = false))
                            cacheVideasyId(mapped.malId, show.id.toString())
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VideasyAPI", "Error fetching discover list: ${e.message}")
                }

                fetchedShows.sortByDescending { it.score }
                _tvShowsList.value = fetchedShows.ifEmpty { localTvShows }
            } catch (e: Exception) {
                _tvShowsList.value = localTvShows
            }

            try {
                val videasy = RetrofitInstance.videasyApi
                val movieQueries = listOf(
                    "Interstellar", "Inception", "The Dark Knight", "Oppenheimer",
                    "Spirited Away", "Your Name", "Parasite", "Titanic", "Avatar", "Gladiator"
                )

                val fetchedMovies = mutableListOf<Anime>()
                for (query in movieQueries) {
                    try {
                        val results = videasy.searchShows(query)
                        val match = results.firstOrNull()?.show
                        if (match != null && !isAnimationContent(match)) {
                            val mapped = mapVideasyShowToAnime(
                                show = match,
                                offset = 10000,
                                fallbackEpisodes = 1,
                                fallbackStatus = "Released",
                                fallbackGenre = "Movie",
                                mediaType = "movie"
                            )
                            if (fetchedMovies.none { it.malId == mapped.malId }) {
                                fetchedMovies.add(mapped)
                            }

                                cacheTmdbId(mapped.malId, resolveTmdbId(match, preferMovie = true))
                                cacheVideasyId(mapped.malId, match.id.toString())
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("VideasyAPI", "Movie search failed for $query: ${e.message}")
                    }
                }

                _moviesList.value = fetchedMovies.ifEmpty { localMovies }
            } catch (e: Exception) {
                _moviesList.value = localMovies
            }

            updateRecommendations()
        }
    }

    private fun isAnimationContent(show: VideasyShow): Boolean {
        val typeLower = show.type?.lowercase() ?: ""
        val nameLower = show.name.lowercase()
        val genresLower = show.genres?.map { it.lowercase() } ?: emptyList()
        return typeLower.contains("animation") ||
            typeLower.contains("anime") ||
            genresLower.contains("anime") ||
            genresLower.contains("animation") ||
            nameLower.contains("anime")
    }

    fun initSeasonalFavorites() {
        viewModelScope.launch {
            seasonalBrowserLoading.value = true
            val seasonsList = listOf("Winter", "Spring", "Summer", "Fall")
            var currentYear = defaultSeasonPair.first
            var currentSeasonIdx = seasonsList.indexOf(defaultSeasonPair.second)
            if (currentSeasonIdx == -1) currentSeasonIdx = 2 // Default to Summer
            
            var attempts = 0
            var found = false
            
            while (attempts < 8 && !found) {
                val seasonName = seasonsList[currentSeasonIdx]
                try {
                    val response = api.getSpecificSeason(currentYear, seasonName.lowercase(), 15)
                    val cleanData = response.data.filterSafe()
                    if (cleanData.isNotEmpty()) {
                        selectedSeasonalYear.value = currentYear
                        selectedSeasonalSeason.value = seasonName
                        currentSeasonal = cleanData
                        
                        val currentState = _homeState.value
                        if (currentState is HomeState.Success) {
                            _homeState.value = currentState.copy(seasonal = currentSeasonal)
                        }
                        found = true
                    }
                } catch (e: Exception) {
                    // Ignore and try previous
                }
                
                if (!found) {
                    currentSeasonIdx--
                    if (currentSeasonIdx < 0) {
                        currentSeasonIdx = 3
                        currentYear--
                    }
                    attempts++
                }
            }
            
            if (!found) {
                // Absolute fallback to current season or local fallback
                try {
                    val response = api.getCurrentSeason(15)
                    val cleanData = response.data.filterSafe()
                    if (cleanData.isNotEmpty()) {
                        currentSeasonal = cleanData
                        val currentState = _homeState.value
                        if (currentState is HomeState.Success) {
                            _homeState.value = currentState.copy(seasonal = currentSeasonal)
                        }
                    }
                } catch (e: Exception) {
                    currentSeasonal = localSeasonalAnime
                }
            }
            
            seasonalBrowserLoading.value = false
            updateRecommendations()
        }
    }

    fun loadTrending() {
        viewModelScope.launch {
            _homeState.value = HomeState.Loading
            try {
                // Try fetching real API data first
                val trendingResponse = api.getTrendingAnime("airing", 15)
                currentTrending = trendingResponse.data.filterSafe().ifEmpty { localTrendingAnime }
                
                val popularResponse = api.getTrendingAnime("bypopularity", 15)
                currentPopular = popularResponse.data.filterSafe().ifEmpty { localPopularAnime }
                
                val upcomingResponse = api.getTrendingAnime("upcoming", 15)
                currentUpcoming = upcomingResponse.data.filterSafe().ifEmpty { localUpcomingAnime }
                
                try {
                    val seasonalResponse = api.getSpecificSeason(defaultSeasonPair.first, defaultSeasonPair.second.lowercase(), 15)
                    currentSeasonal = seasonalResponse.data.filterSafe().ifEmpty { localSeasonalAnime }
                } catch (e: Exception) {
                    try {
                        val seasonalResponse = api.getCurrentSeason(15)
                        currentSeasonal = seasonalResponse.data.filterSafe().ifEmpty { localSeasonalAnime }
                    } catch (e2: Exception) {
                        currentSeasonal = localSeasonalAnime
                    }
                }

                _homeState.value = HomeState.Success(
                    trending = currentTrending,
                    popular = currentPopular,
                    upcoming = currentUpcoming,
                    seasonal = currentSeasonal
                )
                updateRecommendations()
                
                // Fetch and auto-select latest active season that has real API data!
                initSeasonalFavorites()
            } catch (e: Exception) {
                // Use curated offline Fallbacks
                currentTrending = localTrendingAnime
                currentPopular = localPopularAnime
                currentUpcoming = localUpcomingAnime
                currentSeasonal = localSeasonalAnime
                
                _homeState.value = HomeState.Success(
                    trending = currentTrending,
                    popular = currentPopular,
                    upcoming = currentUpcoming,
                    seasonal = currentSeasonal
                )
                updateRecommendations()
                
                // Fallback attempt
                initSeasonalFavorites()
            }
        }
    }

    fun getAnimeCharacters(malId: Int): List<String> {
        return when (malId) {
            1 -> listOf("Tanjiro", "Nezuko", "Zenitsu", "Inosuke", "Muzan", "Kyojuro", "Giyu", "Shinobu")
            2 -> listOf("Eren", "Mikasa", "Armin", "Levi", "Erwin", "Reiner", "Zeke", "Hange")
            3 -> listOf("Sung Jinwoo", "Cha Hae-In", "Yoo Jinho", "Baek Yoonho")
            4 -> listOf("Yuji Itadori", "Megumi Fushiguro", "Nobara Kugisaki", "Satoru Gojo", "Ryomen Sukuna", "Nanami")
            5, 10 -> listOf("Denji", "Power", "Aki", "Makima", "Pochita", "Reze")
            6 -> listOf("Edward Elric", "Alphonse Elric", "Roy Mustang", "Winry", "Scar")
            7 -> listOf("Light Yagami", "L", "Ryuk", "Misa Amane", "Near", "Mello")
            8 -> listOf("Gon Freecss", "Killua Zoldyck", "Kurapika", "Leorio", "Hisoka")
            9 -> listOf("Izuku Midoriya", "Katsuki Bakugo", "Shoto Todoroki", "All Might")
            11 -> listOf("Ichigo Kurosaki", "Rukia Kuchiki", "Uryu Ishida", "Yhwach", "Aizen")
            12 -> listOf("Frieren", "Fern", "Stark", "Himmel", "Heiter", "Eisen")
            13 -> listOf("Loid Forger", "Anya Forger", "Yor Forger", "Bond")
            14 -> listOf("Chihiro", "Haku", "No-Face", "Yubaba")
            15 -> listOf("Taki Tachibana", "Mitsuha Miyamizu")
            16 -> listOf("Shoya Ishida", "Shoko Nishimiya")
            17 -> listOf("Kaguya Shinomiya", "Miyuki Shirogane", "Chika Fujiwara", "Yu Ishigami")
            18 -> listOf("Ai Hoshino", "Aquamarine Hoshino", "Ruby Hoshino", "Kana Arima", "Akane Kurokawa")
            else -> emptyList()
        }
    }

    fun getAnimeStaff(malId: Int): List<Pair<String, String>> {
        return when (malId) {
            1 -> listOf("Haruo Sotozaki" to "Director", "Ufotable" to "Studio", "Yuki Kajiura" to "Music Composer")
            2 -> listOf("Tetsuro Araki" to "Director", "Wit Studio" to "Animation Studio", "Hiroyuki Sawano" to "Music Composer")
            3 -> listOf("Shunsuke Nakashige" to "Director", "A-1 Pictures" to "Animation Studio", "Hiroyuki Sawano" to "Music Composer")
            4 -> listOf("Sunghoo Park" to "Director", "MAPPA" to "Animation Studio", "Yoshimasa Terui" to "Music Composer")
            5, 10 -> listOf("Ryu Nakayama" to "Director", "MAPPA" to "Animation Studio", "Kensuke Ushio" to "Music Composer")
            6 -> listOf("Yasuhiro Irie" to "Director", "Bones" to "Animation Studio", "Akira Senju" to "Music Composer")
            7 -> listOf("Tetsuro Araki" to "Director", "Madhouse" to "Animation Studio", "Yoshihisa Hirano" to "Music Composer")
            8 -> listOf("Hiroshi Kojina" to "Director", "Madhouse" to "Animation Studio", "Yoshihisa Hirano" to "Music Composer")
            9 -> listOf("Kenji Nagasaki" to "Director", "Bones" to "Animation Studio", "Yuki Hayashi" to "Music Composer")
            11 -> listOf("Noriyuki Abe" to "Director", "Studio Pierrot" to "Animation Studio", "Shiro Sagisu" to "Music Composer")
            12 -> listOf("Keiichiro Saito" to "Director", "Madhouse" to "Animation Studio", "Evan Call" to "Music Composer")
            13 -> listOf("Kazuhiro Furuhashi" to "Director", "CloverWorks & Wit Studio" to "Animation Studio", "Know_Name" to "Music Composer")
            14 -> listOf("Hayao Miyazaki" to "Director", "Studio Ghibli" to "Animation Studio", "Joe Hisaishi" to "Music Composer")
            15 -> listOf("Makoto Shinkai" to "Director", "CoMix Wave Films" to "Animation Studio", "Radwimps" to "Music Composer")
            16 -> listOf("Naoko Yamada" to "Director", "Kyoto Animation" to "Animation Studio", "Kensuke Ushio" to "Music Composer")
            17 -> listOf("Shinichi Omata" to "Director", "A-1 Pictures" to "Animation Studio", "Kei Haneoka" to "Music Composer")
            18 -> listOf("Daisuke Hiramaki" to "Director", "Doga Kobo" to "Animation Studio", "Takuro Iga" to "Music Composer")
            else -> listOf("Atsuko Ishizuka" to "Director", "Madhouse" to "Animation Studio", "Yoshiaki Dewa" to "Music Composer")
        }
    }

    fun getCombinedAnimeList(): List<Anime> {
        val lists = mutableListOf<Anime>()
        val state = _homeState.value
        if (state is HomeState.Success) {
            lists.addAll(state.trending)
            lists.addAll(state.popular)
            lists.addAll(state.upcoming)
            lists.addAll(state.seasonal)
        } else {
            lists.addAll(localTrendingAnime)
            lists.addAll(localPopularAnime)
            lists.addAll(localUpcomingAnime)
            lists.addAll(localSeasonalAnime)
        }
        lists.addAll(_moviesList.value)
        lists.addAll(_tvShowsList.value)
        return lists.distinctBy { it.malId }
    }

    fun getAnimeOnlyList(): List<Anime> {
        val lists = mutableListOf<Anime>()
        val state = _homeState.value
        if (state is HomeState.Success) {
            lists.addAll(state.trending)
            lists.addAll(state.popular)
            lists.addAll(state.upcoming)
            lists.addAll(state.seasonal)
        } else {
            lists.addAll(localTrendingAnime)
            lists.addAll(localPopularAnime)
            lists.addAll(localUpcomingAnime)
            lists.addAll(localSeasonalAnime)
        }
        return lists.distinctBy { it.malId }
    }

    fun searchAnime(query: String) {
        if (query.isBlank()) {
            _homeState.value = HomeState.Success(
                trending = currentTrending,
                popular = currentPopular,
                upcoming = currentUpcoming,
                seasonal = currentSeasonal
            )
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _homeState.value = HomeState.Loading
            
            val animeResults = mutableListOf<Anime>()
            val showResults = mutableListOf<Anime>()
            
            // 1. Search Anime via Jikan API
            try {
                val response = api.searchAnime(query = query)
                animeResults.addAll(response.data.filterSafe())
            } catch (e: Exception) {
                android.util.Log.e("UniversalSearch", "Anime search failed: ${e.message}")
            }
            
            // 2. Search Shows & Movies via Videasy API
            try {
                val videasy = RetrofitInstance.videasyApi
                val results = videasy.searchShows(query)
                results.forEach { result ->
                    val match = result.show
                    if (isAnimationContent(match)) {
                        return@forEach
                    }

                    val typeLower = match.type?.lowercase() ?: ""
                    val isMovie = typeLower.contains("movie")
                    val mapped = mapVideasyShowToAnime(
                        show = match,
                        offset = if (isMovie) 10000 else 20000,
                        fallbackEpisodes = if (isMovie) 1 else 12,
                        fallbackStatus = if (isMovie) "Released" else "Running",
                        fallbackGenre = if (isMovie) "Movie" else "Drama",
                        mediaType = if (isMovie) "movie" else "tv"
                    )
                    showResults.add(mapped)

                    cacheTmdbId(mapped.malId, resolveTmdbId(match, preferMovie = if (isMovie) true else false))
                    cacheVideasyId(mapped.malId, match.id.toString())
                }
            } catch (e: Exception) {
                android.util.Log.e("UniversalSearch", "Shows search failed: ${e.message}")
            }
            
            // 3. Local filtering fallback with character names support
            val localResults = (localTrendingAnime + localPopularAnime + localSeasonalAnime + localMovies + localTvShows)
                .filter { anime ->
                    val titleMatches = anime.title.contains(query, ignoreCase = true) || 
                                       anime.titleEnglish?.contains(query, ignoreCase = true) == true
                    val characterMatches = getAnimeCharacters(anime.malId).any { it.contains(query, ignoreCase = true) }
                    titleMatches || characterMatches
                }
                .filterSafe()
            
            // Combine all results
            val combinedResults = (animeResults + showResults + localResults)
                .distinctBy { it.malId }
                .sortedByDescending { it.score ?: 0.0 }
                
            _searchResults.value = combinedResults
            
            _homeState.value = HomeState.Success(
                trending = currentTrending,
                popular = currentPopular,
                upcoming = currentUpcoming,
                seasonal = combinedResults
            )
        }
    }

    fun getCurrentYearAndSeason(): Pair<Int, String> {
        val calendar = java.util.Calendar.getInstance()
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) // 0-indexed: 0 = Jan, 11 = Dec
        val season = when (month) {
            in java.util.Calendar.JANUARY..java.util.Calendar.MARCH -> "Winter"
            in java.util.Calendar.APRIL..java.util.Calendar.JUNE -> "Spring"
            in java.util.Calendar.JULY..java.util.Calendar.SEPTEMBER -> "Summer"
            else -> "Fall"
        }
        return Pair(year, season)
    }

    private val defaultSeasonPair = getCurrentYearAndSeason()
    val searchQuery = MutableStateFlow("")
    val searchGenre = MutableStateFlow("")

    fun setSearchGenre(genre: String) {
        searchGenre.value = genre
        searchAnime(searchQuery.value)
    }

    val selectedSeasonalYear = MutableStateFlow(defaultSeasonPair.first)
    val selectedSeasonalSeason = MutableStateFlow(defaultSeasonPair.second)
    val seasonalBrowserLoading = MutableStateFlow(false)

    fun changeSeason(year: Int, season: String) {
        selectedSeasonalYear.value = year
        selectedSeasonalSeason.value = season
        
        viewModelScope.launch {
            seasonalBrowserLoading.value = true
            try {
                val response = api.getSpecificSeason(year, season.lowercase(), 15)
                val cleanData = response.data.filterSafe()
                currentSeasonal = cleanData.ifEmpty { localSeasonalAnime }
                
                val currentState = _homeState.value
                if (currentState is HomeState.Success) {
                    _homeState.value = currentState.copy(seasonal = currentSeasonal)
                }
            } catch (e: Exception) {
                currentSeasonal = localSeasonalAnime
                val currentState = _homeState.value
                if (currentState is HomeState.Success) {
                    _homeState.value = currentState.copy(seasonal = currentSeasonal)
                }
            } finally {
                seasonalBrowserLoading.value = false
                updateRecommendations()
            }
        }
    }

    private val _searchResults = MutableStateFlow<List<Anime>>(emptyList())
    val searchResults: StateFlow<List<Anime>> = _searchResults.asStateFlow()

    fun findAnimeInLoadedLists(id: Int): Anime? {
        val lists = mutableListOf<Anime>()
        lists.addAll(currentTrending)
        lists.addAll(currentPopular)
        lists.addAll(currentUpcoming)
        lists.addAll(currentSeasonal)
        lists.addAll(_moviesList.value)
        lists.addAll(_tvShowsList.value)
        lists.addAll(_searchResults.value)
        lists.addAll(localTrendingAnime)
        lists.addAll(localPopularAnime)
        lists.addAll(localUpcomingAnime)
        lists.addAll(localSeasonalAnime)
        lists.addAll(localMovies)
        lists.addAll(localTvShows)
        return lists.find { it.malId == id }
    }

    fun loadAnimeDetails(id: Int) {
        viewModelScope.launch {
            val cached = findAnimeInLoadedLists(id)
            if (cached != null) {
                _detailState.value = DetailState.Success(cached)
            } else {
                _detailState.value = DetailState.Loading
            }
            
            // Only fetch from Jikan API for anime (IDs < 10000)
            // Movies (10000-19999) and TV shows (20000-29999) are Videasy data
            if (id < 10000) {
                try {
                    val response = api.getAnimeDetails(id)
                    _detailState.value = DetailState.Success(response.data)
                } catch (e: Exception) {
                    if (_detailState.value !is DetailState.Success) {
                        val fallback = findAnimeInLoadedLists(id)
                        if (fallback != null) {
                            _detailState.value = DetailState.Success(fallback)
                        } else {
                            _detailState.value = DetailState.Error(e.message ?: "Unknown error")
                        }
                    }
                }
            } else {
                // For movies/TV shows, just use what we already have cached
                if (_detailState.value !is DetailState.Success) {
                    _detailState.value = DetailState.Error("Show not found. Please try again.")
                }
            }
        }
    }

    // --- Authentication ---
    fun clearAuthError() {
        _authError.value = null
    }

    fun register(email: String, username: String, pass: String): Boolean {
        if (email.isBlank() || username.isBlank() || pass.isBlank()) {
            _authError.value = "All fields are required"
            return false
        }
        val cleanUser = username.trim()
        val cleanEmail = email.trim()

        if (cleanUser.length < 3) {
            _authError.value = "Username must be at least 3 characters"
            return false
        }
        if (pass.length < 4) {
            _authError.value = "Password must be at least 4 characters"
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            _authError.value = "Please enter a valid email address"
            return false
        }

        val userListStr = prefs.getString("registered_user_list", "") ?: ""
        val userList = userListStr.split(",").filter { it.isNotBlank() }.map { it.lowercase() }
        if (userList.contains(cleanUser.lowercase())) {
            _authError.value = "Username is already taken!"
            return false
        }

        prefs.edit()
            .putString("user_pass_${cleanUser.lowercase()}", pass)
            .putString("user_email_${cleanUser.lowercase()}", cleanEmail)
            .putString("user_avatar_${cleanUser.lowercase()}", "https://api.dicebear.com/7.x/adventurer/svg?seed=$cleanUser")
            .putString("user_banner_${cleanUser.lowercase()}", "")
            .putInt("user_level_${cleanUser.lowercase()}", 1)
            .putInt("user_xp_${cleanUser.lowercase()}", 25)
            .putInt("user_xpNeeded_${cleanUser.lowercase()}", 100)
            .putInt("user_watchTime_${cleanUser.lowercase()}", 0)
            .putString("registered_user_list", if (userListStr.isEmpty()) cleanUser else "$userListStr,$cleanUser")
            .apply()

        return loginWithPassword(cleanUser, pass)
    }

    fun loginWithPassword(identifier: String, pass: String): Boolean {
        if (identifier.isBlank() || pass.isBlank()) {
            _authError.value = "All fields are required"
            return false
        }
        val cleanId = identifier.trim()

        var matchedUsername: String? = null
        val userListStr = prefs.getString("registered_user_list", "") ?: ""
        val userList = userListStr.split(",").filter { it.isNotBlank() }

        val targetUsername = userList.find { it.equals(cleanId, ignoreCase = true) }
        if (targetUsername != null) {
            matchedUsername = targetUsername
        } else {
            for (u in userList) {
                val email = prefs.getString("user_email_${u.lowercase()}", "")
                if (email.equals(cleanId, ignoreCase = true)) {
                    matchedUsername = u
                    break
                }
            }
        }

        if (matchedUsername == null) {
            _authError.value = "Account not found. Please sign up first."
            return false
        }

        val storedPass = prefs.getString("user_pass_${matchedUsername.lowercase()}", null)
        if (storedPass != pass) {
            _authError.value = "Incorrect password. Please try again."
            return false
        }

        val email = prefs.getString("user_email_${matchedUsername.lowercase()}", "") ?: ""
        val avatar = prefs.getString("user_avatar_${matchedUsername.lowercase()}", "https://api.dicebear.com/7.x/adventurer/svg?seed=$matchedUsername") ?: "https://api.dicebear.com/7.x/adventurer/svg?seed=$matchedUsername"
        val banner = prefs.getString("user_banner_${matchedUsername.lowercase()}", "") ?: ""
        val level = prefs.getInt("user_level_${matchedUsername.lowercase()}", 1)
        val xp = prefs.getInt("user_xp_${matchedUsername.lowercase()}", 25)
        val xpNeeded = prefs.getInt("user_xpNeeded_${matchedUsername.lowercase()}", 100)
        val watchTime = prefs.getInt("user_watchTime_${matchedUsername.lowercase()}", 0)

        val user = User(matchedUsername, email, avatar, banner, level, xp, xpNeeded, watchTime)
        _currentUser.value = user
        saveCurrentUserToPrefs(user)
        _authError.value = null
        return true
    }

    fun login(email: String, username: String): Boolean {
        if (email.isNotBlank() && username.isNotBlank()) {
            val user = User(
                username = username,
                email = email,
                level = 1,
                xp = 25,
                xpNeeded = 100,
                totalWatchTime = 48
            )
            _currentUser.value = user
            saveCurrentUserToPrefs(user)
            return true
        }
        return false
    }

    fun logout() {
        _currentUser.value = null
        clearCurrentUserPrefs()
    }

    fun updateProfile(username: String, avatarUrl: String, bannerUrl: String) {
        _currentUser.value?.let { user ->
            val updated = user.copy(
                username = username,
                avatarUrl = avatarUrl,
                bannerUrl = bannerUrl
            )
            _currentUser.value = updated
            saveCurrentUserToPrefs(updated)

            prefs.edit()
                .putString("user_avatar_${user.username.lowercase()}", avatarUrl)
                .putString("user_banner_${user.username.lowercase()}", bannerUrl)
                .apply()
        }
    }

    fun updateRecommendations() {
        val favs = _favorites.value
        val watched = _continueWatching.value.map { it.first }
        val allInteracted = (favs + watched).distinctBy { it.malId }
        
        // Extract favorite genres
        val genreCounts = allInteracted.flatMap { it.genres ?: emptyList() }
            .map { it.name.lowercase() }
            .groupBy { it }
            .mapValues { it.value.size }
            
        // Get all available unique anime across lists
        val allAvailable = mutableListOf<Anime>()
        val hState = _homeState.value
        if (hState is HomeState.Success) {
            allAvailable.addAll(hState.trending)
            allAvailable.addAll(hState.popular)
            allAvailable.addAll(hState.upcoming)
            allAvailable.addAll(hState.seasonal)
        } else {
            allAvailable.addAll(localTrendingAnime)
            allAvailable.addAll(localPopularAnime)
            allAvailable.addAll(localUpcomingAnime)
            allAvailable.addAll(localSeasonalAnime)
        }
        allAvailable.addAll(_moviesList.value)
        allAvailable.addAll(_tvShowsList.value)
        
        val uniqueAvailable = allAvailable.distinctBy { it.malId }
        
        val recList = if (genreCounts.isNotEmpty()) {
            uniqueAvailable.filter { anime ->
                val isInteracted = allInteracted.any { it.malId == anime.malId }
                val hasMatchingGenre = anime.genres?.any { g -> genreCounts.containsKey(g.name.lowercase()) } == true
                hasMatchingGenre && !isInteracted
            }.sortedByDescending { anime ->
                val matchCount = anime.genres?.count { g -> genreCounts.containsKey(g.name.lowercase()) } ?: 0
                matchCount * 2.5 + (anime.score ?: 0.0)
            }
        } else {
            emptyList()
        }
        
        if (recList.size < 6) {
            val remaining = uniqueAvailable.filter { anime ->
                !allInteracted.any { it.malId == anime.malId } && !recList.any { it.malId == anime.malId }
            }.sortedByDescending { it.score ?: 0.0 }
            _recommendations.value = (recList + remaining).distinctBy { it.malId }.take(10)
        } else {
            _recommendations.value = recList.take(10)
        }
    }

    // --- Favorites ---
    fun toggleFavorite(anime: Anime) {
        val currentList = _favorites.value.toMutableList()
        val existing = currentList.find { it.malId == anime.malId }
        if (existing != null) {
            currentList.remove(existing)
        } else {
            currentList.add(anime)
        }
        _favorites.value = currentList
        updateRecommendations()
    }

    fun isFavorite(animeId: Int): Boolean {
        return _favorites.value.any { it.malId == animeId }
    }

    // --- Continue Watching & XP Progress ---
    fun watchEpisode(anime: Anime, episodeNum: Int) {
        // 1. Update list
        val currentList = _continueWatching.value.toMutableList()
        val index = currentList.indexOfFirst { it.first.malId == anime.malId }
        if (index != -1) {
            currentList[index] = Pair(anime, episodeNum)
        } else {
            currentList.add(0, Pair(anime, episodeNum))
        }
        _continueWatching.value = currentList
        updateRecommendations()

        // 2. Increment stats
        _currentUser.value?.let { user ->
            var newXp = user.xp + 15
            var newLevel = user.level
            var newXpNeeded = user.xpNeeded
            if (newXp >= newXpNeeded) {
                newLevel += 1
                newXp -= newXpNeeded
                newXpNeeded = (newXpNeeded * 1.2).toInt() // game progression!
            }
            _currentUser.value = user.copy(
                level = newLevel,
                xp = newXp,
                xpNeeded = newXpNeeded,
                totalWatchTime = user.totalWatchTime + 24
            )
        }
    }

    // --- Reminders ---
    fun toggleReminder(airingId: Int) {
        val current = _reminders.value.toMutableSet()
        if (current.contains(airingId)) {
            current.remove(airingId)
        } else {
            current.add(airingId)
        }
        _reminders.value = current
        saveRemindersToPrefs(current)
    }

    private fun loadRemindersFromPrefs() {
        val stored = prefs.getString(reminderPrefsKey, null)
        if (!stored.isNullOrBlank()) {
            val ids = stored.split(",").mapNotNull { it.toIntOrNull() }.toSet()
            _reminders.value = ids
        }
    }

    private fun saveRemindersToPrefs(ids: Set<Int>) {
        prefs.edit().putString(reminderPrefsKey, ids.joinToString(",")).apply()
    }

    fun isReminded(airingId: Int): Boolean {
        return _reminders.value.contains(airingId)
    }

    private fun List<Anime>.filterSafe(): List<Anime> {
        val adultOrNsfwGenres = listOf("Hentai", "Erotica", "Ecchi", "Adult", "18+", "Rx", "Nudity", "Sexual")
        return this.filter { anime ->
            val titleLower = anime.title.lowercase()
            val engTitleLower = anime.titleEnglish?.lowercase() ?: ""
            val synopsisLower = anime.synopsis?.lowercase() ?: ""

            val isAdultGenre = anime.genres?.any { g ->
                adultOrNsfwGenres.any { ag -> g.name.equals(ag, ignoreCase = true) }
            } == true ||
            titleLower.contains("uncensored") || engTitleLower.contains("uncensored") ||
            titleLower.contains("hentai") || engTitleLower.contains("hentai") ||
            titleLower.contains("ecchi") || engTitleLower.contains("ecchi") ||
            titleLower.contains("erotica") || engTitleLower.contains("erotica") ||
            synopsisLower.contains("18+") || synopsisLower.contains("adult anime")

            !isAdultGenre
        }
    }
}
