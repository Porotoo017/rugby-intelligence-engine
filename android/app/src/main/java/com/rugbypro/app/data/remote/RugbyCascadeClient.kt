package com.rugbypro.app.data.remote

import com.rugbypro.app.domain.model.MatchDayPart
import com.rugbypro.app.domain.model.MatchStatus
import com.rugbypro.app.domain.model.MatchWeather
import com.rugbypro.app.domain.model.NewsArticle
import com.rugbypro.app.domain.model.RugbyMatch
import com.rugbypro.app.domain.model.SourceHealthItem
import com.rugbypro.app.domain.usecase.CrossVerifyMatchesUseCase
import com.rugbypro.app.domain.usecase.FilterLiveAndTodayMatchesUseCase
import com.rugbypro.app.domain.usecase.PreMatchWindowUseCase
import com.rugbypro.app.domain.usecase.RugbyDateUtils
import com.rugbypro.app.domain.usecase.WeatherClassificationUseCase
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

interface DynamicRugbyApi {
    @GET
    suspend fun fetchRaw(@Url url: String): ResponseBody
}

data class CascadeSyncResult(
    val snapshotJson: String?,
    val worldRugbyMatches: List<RugbyMatch>,
    val secondaryLiveMatches: List<RugbyMatch>,
    val healthItems: List<SourceHealthItem>,
    val liveNews: List<NewsArticle> = emptyList()
)

class RugbyCascadeClient {
    private val geocodeCache = ConcurrentHashMap<String, Pair<Double, Double>>()
    private val weatherCache = ConcurrentHashMap<String, Pair<Long, MatchWeather>>()

    private fun buildApi(timeoutMs: Long): DynamicRugbyApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .addInterceptor { chain ->
                val req: Request = chain.request().newBuilder()
                    .header("User-Agent", "RugbyPro/4.0 (Android)")
                    .header("Accept", "application/json, application/xml, text/xml, */*")
                    .build()
                chain.proceed(req)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl("https://porotoo017.github.io/")
            .client(client)
            .build()
            .create(DynamicRugbyApi::class.java)
    }

    private suspend fun fetchWithBackoffAndRotation(
        api: DynamicRugbyApi,
        urls: List<String>,
        maxAttemptsPerUrl: Int = 2
    ): Pair<String?, Long> {
        val started = System.currentTimeMillis()
        for (url in urls) {
            var waitMs = 350L
            repeat(maxAttemptsPerUrl) {
                val res = runCatching { api.fetchRaw(url).string() }.getOrNull()
                if (!res.isNullOrBlank()) {
                    return res to (System.currentTimeMillis() - started)
                }
                delay(waitMs)
                waitMs = (waitMs * 2).coerceAtMost(1800L)
            }
        }
        return null to (System.currentTimeMillis() - started)
    }

    suspend fun executeCascade(
        timeoutMs: Long,
        includeSnapshot: Boolean
    ): CascadeSyncResult {
        val api = buildApi(timeoutMs.coerceIn(4000L, 25000L))
        val tz = TimeZone.getTimeZone("America/Argentina/Buenos_Aires")
        val nowLabel = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).apply { timeZone = tz }.format(Date())
        val health = mutableListOf<SourceHealthItem>()

        // Nivel 1: Ventana temporal real (-2 días a +7 días UTC) para capturar SVNS 7s, Tests y ligas de hoy
        val nowInstant = Instant.now()
        val startDate = nowInstant.minus(2, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC).toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        val endDate = nowInstant.plus(7, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC).toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)

        val wrMatchesCollected = mutableListOf<RugbyMatch>()
        var wrAnyOk = false
        val wrStarted = System.currentTimeMillis()
        for (page in 0..1) {
            val pageUrl = "https://api.wr-rims-prod.pulselive.com/rugby/v3/match" +
                "?startDate=$startDate&endDate=$endDate&pageSize=100&page=$page&sort=asc"
            val (wrRaw, _) = fetchWithBackoffAndRotation(api, listOf(pageUrl), maxAttemptsPerUrl = 2)
            if (wrRaw != null) {
                wrAnyOk = true
                val parsed = runCatching { RugbyJsonParser.parseWorldRugbyMatches(wrRaw) }.getOrDefault(emptyList())
                wrMatchesCollected.addAll(parsed)
                if (parsed.size < 95) break
            } else {
                break
            }
        }
        if (wrMatchesCollected.isEmpty()) {
            val fallbackUrl = "https://api.wr-rims-prod.pulselive.com/rugby/v3/match?states=L,U&pageSize=60&sort=desc"
            val (fbRaw, _) = fetchWithBackoffAndRotation(api, listOf(fallbackUrl), maxAttemptsPerUrl = 1)
            if (fbRaw != null) {
                wrAnyOk = true
                wrMatchesCollected.addAll(runCatching { RugbyJsonParser.parseWorldRugbyMatches(fbRaw) }.getOrDefault(emptyList()))
            }
        }
        val wrMatches = wrMatchesCollected.distinctBy { it.id }
        val wrLat = System.currentTimeMillis() - wrStarted
        health.add(
            SourceHealthItem(
                id = "tier1_official",
                name = "Official Feed",
                tier = 1,
                statusLabel = if (wrAnyOk) "OK" else "FAIL",
                isHealthy = wrAnyOk,
                latencyMs = wrLat,
                detail = "${wrMatches.size}",
                checkedAtArg = nowLabel
            )
        )

        var snapshotJson: String? = null
        if (includeSnapshot) {
            val snapUrls = listOf(
                "https://porotoo017.github.io/rugby-intelligence-engine/data/rugby_engine_output.json",
                "https://raw.githubusercontent.com/Porotoo017/rugby-intelligence-engine/main/data/rugby_engine_output.json"
            )
            val (snapRaw, snapLat) = fetchWithBackoffAndRotation(api, snapUrls)
            if (snapRaw != null && snapRaw.trimStart().startsWith("{")) {
                snapshotJson = snapRaw
            }
            health.add(
                SourceHealthItem(
                    id = "tier1_snapshot",
                    name = "Snapshot Feed",
                    tier = 1,
                    statusLabel = if (snapshotJson != null) "OK" else "CACHE",
                    isHealthy = snapshotJson != null,
                    latencyMs = snapLat,
                    detail = if (snapshotJson != null) "OK" else "CACHE",
                    checkedAtArg = nowLabel
                )
            )
        }

        // Nivel 2: Scoreboard global + ligas secundarias (15s y 7s)
        val secondaryCollected = mutableListOf<RugbyMatch>()
        val secStarted = System.currentTimeMillis()
        var secSuccessCount = 0

        val (panelRaw, _) = fetchWithBackoffAndRotation(
            api,
            listOf("https://site.api.espn.com/apis/site/v2/sports/rugby/scorepanel"),
            maxAttemptsPerUrl = 1
        )
        if (panelRaw != null) {
            secSuccessCount++
            runCatching { RugbyJsonParser.parseEspnScorepanel(panelRaw) }
                .getOrNull()?.let { secondaryCollected.addAll(it) }
        }

        val secondaryLeagues = listOf(
            "289234" to "Internacionales",
            "24400" to "Nations Championship",
            "270557" to "Six Nations",
            "242041" to "Rugby Championship",
            "270559" to "Top 14",
            "267979" to "URC",
            "270555" to "Premiership Rugby",
            "272073" to "Champions Cup",
            "282" to "Sevens Masculino",
            "283" to "Sevens Femenino"
        )
        for ((lid, lName) in secondaryLeagues) {
            val sbUrl = "https://site.api.espn.com/apis/site/v2/sports/rugby/$lid/scoreboard"
            val (sbRaw, _) = fetchWithBackoffAndRotation(api, listOf(sbUrl), maxAttemptsPerUrl = 1)
            if (sbRaw != null) {
                secSuccessCount++
                runCatching { RugbyJsonParser.parseEspnScoreboard(sbRaw, lid, lName) }
                    .getOrNull()?.let { secondaryCollected.addAll(it) }
            }
        }
        val uniqueSecondary = secondaryCollected.distinctBy { it.id }

        // Enriquecer partidos secundarios en vivo con play-by-play y estadísticas
        val enrichedSecondary = mutableListOf<RugbyMatch>()
        var liveSecEnriched = 0
        for (m in uniqueSecondary) {
            if (m.status == MatchStatus.LIVE && liveSecEnriched < 6) {
                liveSecEnriched++
                val evId = m.id.removePrefix("espn_")
                val sumUrl = "https://site.api.espn.com/apis/site/v2/sports/rugby/${m.leagueId}/summary?event=$evId"
                val (sumRaw, _) = fetchWithBackoffAndRotation(api, listOf(sumUrl), maxAttemptsPerUrl = 1)
                if (sumRaw != null) {
                    val det = runCatching { RugbyJsonParser.parseEspnSummaryFull(sumRaw, m.homeName, m.awayName) }.getOrNull()
                    if (det != null) {
                        enrichedSecondary.add(
                            m.copy(
                                events = det.events,
                                liveStats = det.liveStats,
                                refereeName = det.refereeName.ifBlank { m.refereeName },
                                officials = det.officials.ifEmpty { m.officials },
                                homeLineup = det.homeLineup.ifEmpty { m.homeLineup },
                                awayLineup = det.awayLineup.ifEmpty { m.awayLineup }
                            )
                        )
                        continue
                    }
                }
            }
            enrichedSecondary.add(m)
        }

        health.add(
            SourceHealthItem(
                id = "tier2_secondary",
                name = "Secondary Feed",
                tier = 2,
                statusLabel = if (secSuccessCount > 0) "OK" else "FAIL",
                isHealthy = secSuccessCount > 0,
                latencyMs = System.currentTimeMillis() - secStarted,
                detail = "${enrichedSecondary.size}",
                checkedAtArg = nowLabel
            )
        )

        // Nivel Noticias en vivo (World Rugby ES + EN para SVNS/Internacional + BBC RSS + Córdoba XV RSS)
        val liveNewsList = mutableListOf<NewsArticle>()
        val newsStarted = System.currentTimeMillis()
        val (wrEsNewsRaw, _) = fetchWithBackoffAndRotation(
            api,
            listOf("https://api.wr-rims-prod.pulselive.com/content/worldrugby/text/es?pageSize=22"),
            maxAttemptsPerUrl = 1
        )
        wrEsNewsRaw?.let {
            liveNewsList.addAll(runCatching { RugbyJsonParser.parseWorldRugbyContentNews(it, "es") }.getOrDefault(emptyList()))
        }
        val (wrEnNewsRaw, _) = fetchWithBackoffAndRotation(
            api,
            listOf("https://api.wr-rims-prod.pulselive.com/content/worldrugby/text/en?pageSize=22"),
            maxAttemptsPerUrl = 1
        )
        wrEnNewsRaw?.let {
            liveNewsList.addAll(runCatching { RugbyJsonParser.parseWorldRugbyContentNews(it, "en") }.getOrDefault(emptyList()))
        }
        val (bbcRaw, _) = fetchWithBackoffAndRotation(
            api,
            listOf("https://feeds.bbci.co.uk/sport/rugby-union/rss.xml"),
            maxAttemptsPerUrl = 1
        )
        bbcRaw?.let {
            liveNewsList.addAll(runCatching { RugbyJsonParser.parseRssNews(it, "BBC Rugby", "Internacional") }.getOrDefault(emptyList()))
        }
        val (cbaRaw, _) = fetchWithBackoffAndRotation(
            api,
            listOf("https://www.cordobaxv.com.ar/feed/"),
            maxAttemptsPerUrl = 1
        )
        cbaRaw?.let {
            liveNewsList.addAll(runCatching { RugbyJsonParser.parseRssNews(it, "Córdoba XV", "Los Pumas") }.getOrDefault(emptyList()))
        }

        health.add(
            SourceHealthItem(
                id = "tier2_news",
                name = "News Feed",
                tier = 2,
                statusLabel = if (liveNewsList.isNotEmpty()) "OK" else "CACHE",
                isHealthy = liveNewsList.isNotEmpty(),
                latencyMs = System.currentTimeMillis() - newsStarted,
                detail = "${liveNewsList.size}",
                checkedAtArg = nowLabel
            )
        )

        return CascadeSyncResult(
            snapshotJson = snapshotJson,
            worldRugbyMatches = wrMatches,
            secondaryLiveMatches = enrichedSecondary,
            healthItems = health,
            liveNews = liveNewsList
        )
    }

    private fun extractWorldRugbyId(matchId: String): String? = when {
        matchId.startsWith("wr_") -> matchId.removePrefix("wr_")
        matchId.startsWith("wr:") -> matchId.removePrefix("wr:")
        else -> null
    }

    private suspend fun enrichSingleMatchWithApi(api: DynamicRugbyApi, m: RugbyMatch): RugbyMatch {
        val wrRawId = extractWorldRugbyId(m.id)
        if (wrRawId != null) {
            val base = "https://api.wr-rims-prod.pulselive.com/rugby/v3/match/$wrRawId"
            val (sumRaw, _) = fetchWithBackoffAndRotation(api, listOf("$base/summary", base), maxAttemptsPerUrl = 1)
            if (sumRaw != null) {
                val (tlRaw, _) = fetchWithBackoffAndRotation(api, listOf("$base/timeline"), maxAttemptsPerUrl = 1)
                val (stRaw, _) = fetchWithBackoffAndRotation(api, listOf("$base/stats"), maxAttemptsPerUrl = 1)
                val is7s = m.group.equals("sevens", true) ||
                    m.leagueName.contains("sevens", true) ||
                    m.leagueName.contains("svns", true) ||
                    m.homeName.contains("7s", true)
                val det = runCatching {
                    RugbyJsonParser.parseWorldRugbyMatchDetail(sumRaw, tlRaw, stRaw, isSevensHint = is7s)
                }.getOrNull()
                if (det != null) {
                    return m.copy(
                        status = det.updatedStatus ?: m.status,
                        periodDisplay = det.updatedPeriodDisplay ?: m.periodDisplay,
                        minuteDisplay = det.updatedMinuteDisplay?.takeIf { it.isNotBlank() } ?: m.minuteDisplay,
                        homeScore = det.updatedHomeScore ?: m.homeScore,
                        awayScore = det.updatedAwayScore ?: m.awayScore,
                        refereeName = det.refereeName.ifBlank { m.refereeName },
                        officials = det.officials.ifEmpty { m.officials },
                        homeLineup = det.homeLineup.ifEmpty { m.homeLineup },
                        awayLineup = det.awayLineup.ifEmpty { m.awayLineup },
                        events = if (det.events.isNotEmpty()) det.events else m.events,
                        liveStats = if (det.liveStats?.hasAnyMetric() == true) det.liveStats else m.liveStats
                    )
                }
            }
        } else if (m.id.startsWith("espn_")) {
            val evId = m.id.removePrefix("espn_")
            val url = "https://site.api.espn.com/apis/site/v2/sports/rugby/${m.leagueId}/summary?event=$evId"
            val (raw, _) = fetchWithBackoffAndRotation(api, listOf(url), maxAttemptsPerUrl = 1)
            if (raw != null) {
                val det = runCatching { RugbyJsonParser.parseEspnSummaryFull(raw, m.homeName, m.awayName) }.getOrNull()
                if (det != null) {
                    return m.copy(
                        refereeName = det.refereeName.ifBlank { m.refereeName },
                        officials = det.officials.ifEmpty { m.officials },
                        homeLineup = det.homeLineup.ifEmpty { m.homeLineup },
                        awayLineup = det.awayLineup.ifEmpty { m.awayLineup },
                        events = if (det.events.isNotEmpty()) det.events else m.events,
                        liveStats = if (det.liveStats?.hasAnyMetric() == true) det.liveStats else m.liveStats
                    )
                }
            }
        }
        return m
    }

    suspend fun fetchSingleMatchLiveDetail(
        match: RugbyMatch,
        timeoutMs: Long
    ): RugbyMatch {
        val api = buildApi(timeoutMs.coerceIn(4000L, 14000L))
        return enrichSingleMatchWithApi(api, match)
    }

    suspend fun enrichMatchesWithOfficialDetails(
        matches: List<RugbyMatch>,
        timeoutMs: Long,
        forcePreMatchId: String? = null
    ): List<RugbyMatch> {
        val api = buildApi(timeoutMs.coerceIn(4000L, 14000L))
        val nowMs = System.currentTimeMillis()
        val ordered = mutableListOf<RugbyMatch>()
        val seen = mutableSetOf<String>()
        fun addMatching(pred: (RugbyMatch) -> Boolean, cap: Int) {
            for (m in matches) {
                if (ordered.size >= 24) break
                if (m.id !in seen && pred(m)) {
                    seen.add(m.id)
                    ordered.add(m)
                    if (ordered.count(pred) >= cap) break
                }
            }
        }
        if (!forcePreMatchId.isNullOrBlank()) {
            addMatching({ it.id == forcePreMatchId }, 2)
        }
        addMatching({ it.status == MatchStatus.LIVE }, 12)
        addMatching({ PreMatchWindowUseCase.isInsideThirtyMinuteWindow(it, nowMs) }, 8)
        addMatching({ FilterLiveAndTodayMatchesUseCase.isScheduledToday(it, nowMs) }, 10)
        addMatching({
            it.status == MatchStatus.FINISHED &&
                it.timestampSec > 0L &&
                (nowMs - it.timestampSec * 1000L) in 0L..(12 * 3600_000L) &&
                (it.events.isEmpty() || it.refereeName.isBlank())
        }, 8)

        if (ordered.isEmpty()) return matches
        val updatedById = mutableMapOf<String, RugbyMatch>()
        for (m in ordered) {
            val enriched = runCatching { enrichSingleMatchWithApi(api, m) }.getOrNull()
            if (enriched != null && enriched != m) {
                updatedById[m.id] = enriched
            }
        }
        if (updatedById.isEmpty()) return matches
        return matches.map { updatedById[it.id] ?: it }
    }

    suspend fun enrichMatchesWithWeather(
        matches: List<RugbyMatch>,
        timeoutMs: Long,
        bypassCacheForMatchId: String? = null
    ): List<RugbyMatch> {
        val api = buildApi(timeoutMs.coerceIn(3500L, 12000L))
        val nowMs = System.currentTimeMillis()
        val targets = matches.filter {
            it.id == bypassCacheForMatchId ||
                it.status == MatchStatus.LIVE ||
                PreMatchWindowUseCase.isInsideThirtyMinuteWindow(it, nowMs) ||
                (it.status == MatchStatus.SCHEDULED && (it.isArgentina || it.timestampSec * 1000L - nowMs in -3600_000L..86_400_000L))
        }.take(12)
        if (targets.isEmpty()) return matches

        val weatherByMatchId = mutableMapOf<String, MatchWeather>()
        for (m in targets) {
            val cityOrVenue = m.venueCity.ifBlank { m.venueName }
            if (cityOrVenue.isBlank()) continue
            val dayPart = RugbyDateUtils.inferDayPart(m.timestampSec, m.timeArg)
            val cacheKey = "${cityOrVenue.lowercase()}_${dayPart.name}"
            val forceFresh = m.id == bypassCacheForMatchId || PreMatchWindowUseCase.isInsideThirtyMinuteWindow(m, nowMs)
            val cached = weatherCache[cacheKey]
            if (!forceFresh && cached != null && (nowMs - cached.first) < 12 * 60_000L) {
                weatherByMatchId[m.id] = cached.second
                continue
            }

            val coords = resolveVenueCoords(api, m.venueCity, m.venueName) ?: continue
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${coords.first}&longitude=${coords.second}" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m" +
                "&hourly=precipitation_probability" +
                "&forecast_days=2&timezone=auto"
            val (raw, _) = fetchWithBackoffAndRotation(api, listOf(url), maxAttemptsPerUrl = 1)
            val parsed = raw?.let { parseOpenMeteoForecast(it, dayPart) }
            if (parsed != null && parsed.isAvailable) {
                weatherCache[cacheKey] = nowMs to parsed
                weatherByMatchId[m.id] = parsed
            }
        }
        if (weatherByMatchId.isEmpty()) return matches
        return matches.map { m ->
            weatherByMatchId[m.id]?.let { w -> m.copy(weather = w) } ?: m
        }
    }

    private suspend fun resolveVenueCoords(
        api: DynamicRugbyApi,
        city: String,
        venue: String
    ): Pair<Double, Double>? {
        val keys = listOf(city, venue).map { it.trim().lowercase() }.filter { it.isNotBlank() }
        for (k in keys) {
            KNOWN_RUGBY_COORDS[k]?.let { return it }
            KNOWN_RUGBY_COORDS.entries.firstOrNull { k.contains(it.key) || it.key.contains(k) }?.let { return it.value }
            geocodeCache[k]?.let { return it }
        }
        val query = city.ifBlank { venue }.trim()
        if (query.length < 3) return null
        val encoded = runCatching { URLEncoder.encode(query, "UTF-8") }.getOrNull() ?: return null
        val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=es"
        val (geoRaw, _) = fetchWithBackoffAndRotation(api, listOf(geoUrl), maxAttemptsPerUrl = 1)
        val coords = geoRaw?.let { parseGeocodingCoords(it) }
        if (coords != null) {
            geocodeCache[query.lowercase()] = coords
        }
        return coords
    }

    companion object {
        private val KNOWN_RUGBY_COORDS = mapOf(
            "buenos aires" to (-34.6037 to -58.3816),
            "san isidro" to (-34.4718 to -58.5128),
            "benavidez" to (-34.4114 to -58.6869),
            "vélez" to (-34.6354 to -58.5207),
            "velez" to (-34.6354 to -58.5207),
            "córdoba" to (-31.4201 to -64.1888),
            "cordoba" to (-31.4201 to -64.1888),
            "tucumán" to (-26.8083 to -65.2176),
            "tucuman" to (-26.8083 to -65.2176),
            "mendoza" to (-32.8895 to -68.8458),
            "rosario" to (-32.9442 to -60.6505),
            "salta" to (-24.7821 to -65.4232),
            "montevideo" to (-34.9011 to -56.1645),
            "asunción" to (-25.2637 to -57.5759),
            "asuncion" to (-25.2637 to -57.5759),
            "santiago" to (-33.4489 to -70.6693),
            "london" to (51.4560 to -0.3415),
            "londres" to (51.4560 to -0.3415),
            "twickenham" to (51.4560 to -0.3415),
            "paris" to (48.9245 to 2.3601),
            "parís" to (48.9245 to 2.3601),
            "saint-denis" to (48.9245 to 2.3601),
            "dublin" to (53.3352 to -6.2285),
            "dublín" to (53.3352 to -6.2285),
            "edinburgh" to (55.9422 to -3.2409),
            "edimburgo" to (55.9422 to -3.2409),
            "cardiff" to (51.4782 to -3.1826),
            "rome" to (41.9341 to 12.4547),
            "roma" to (41.9341 to 12.4547),
            "toulouse" to (43.6197 to 1.4167),
            "la rochelle" to (46.1591 to -1.1790),
            "bordeaux" to (44.8378 to -0.5792),
            "pretoria" to (-25.7533 to 28.2229),
            "cape town" to (-33.9035 to 18.4112),
            "ciudad del cabo" to (-33.9035 to 18.4112),
            "johannesburg" to (-26.1975 to 28.0608),
            "johannesburgo" to (-26.1975 to 28.0608),
            "durban" to (-29.8249 to 31.0303),
            "auckland" to (-36.8750 to 174.7448),
            "wellington" to (-41.2731 to 174.7859),
            "christchurch" to (-43.5432 to 172.6003),
            "dunedin" to (-45.8691 to 170.5250),
            "sydney" to (-33.8471 to 151.0634),
            "melbourne" to (-37.8251 to 144.9838),
            "brisbane" to (-27.4648 to 153.0095),
            "perth" to (-31.9462 to 115.8694),
            "dubai" to (25.0757 to 55.1436),
            "dubái" to (25.0757 to 55.1436),
            "tokyo" to (35.6641 to 139.5271),
            "tokio" to (35.6641 to 139.5271)
        )

        fun parseGeocodingCoords(jsonStr: String): Pair<Double, Double>? {
            return runCatching {
                val root = JSONObject(jsonStr)
                val first = root.optJSONArray("results")?.optJSONObject(0) ?: return null
                val lat = first.optDouble("latitude", Double.NaN)
                val lon = first.optDouble("longitude", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) null else lat to lon
            }.getOrNull()
        }

        fun parseOpenMeteoForecast(
            jsonStr: String,
            dayPart: MatchDayPart
        ): MatchWeather? {
            return runCatching {
                val root = JSONObject(jsonStr)
                val cur = root.optJSONObject("current") ?: return null
                val temp = cur.optDouble("temperature_2m", Double.NaN).takeIf { !it.isNaN() }
                val feels = cur.optDouble("apparent_temperature", Double.NaN).takeIf { !it.isNaN() }
                val humidity = if (cur.has("relative_humidity_2m") && !cur.isNull("relative_humidity_2m")) {
                    cur.optInt("relative_humidity_2m")
                } else null
                val wmo = if (cur.has("weather_code") && !cur.isNull("weather_code")) {
                    cur.optInt("weather_code")
                } else null
                val windSpeed = cur.optDouble("wind_speed_10m", Double.NaN).takeIf { !it.isNaN() }
                val windDeg = cur.optDouble("wind_direction_10m", Double.NaN).takeIf { !it.isNaN() }

                val hourlyProb = root.optJSONObject("hourly")
                    ?.optJSONArray("precipitation_probability")
                    ?.let { arr ->
                        if (arr.length() > 0 && !arr.isNull(0)) arr.optInt(0) else null
                    }

                val (condLabel, condCode) = WeatherClassificationUseCase.mapWmoCondition(wmo, windSpeed)
                val tz = TimeZone.getDefault()
                val updatedAt = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = tz }.format(Date())

                MatchWeather(
                    tempC = temp,
                    feelsLikeC = feels,
                    conditionLabel = condLabel,
                    conditionCode = condCode,
                    rainProbabilityPct = hourlyProb,
                    windSpeedKmh = windSpeed,
                    windDirection = WeatherClassificationUseCase.windCompass(windDeg),
                    humidityPct = humidity,
                    dayPart = dayPart,
                    thermalZone = WeatherClassificationUseCase.thermalZoneFor(temp),
                    updatedAtLabel = updatedAt,
                    isAvailable = temp != null
                )
            }.getOrNull()
        }
    }
}
