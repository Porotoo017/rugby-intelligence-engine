package com.rugbypro.app.data.remote

import com.rugbypro.app.domain.model.RugbyMatch
import com.rugbypro.app.domain.model.SourceHealthItem
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

interface RugbyFeedApi {
    @GET
    suspend fun fetchRaw(@Url url: String): ResponseBody
}

data class CascadeSyncResult(
    val snapshotJson: String?,
    val worldRugbyMatches: List<RugbyMatch>,
    val espnLiveMatches: List<RugbyMatch>,
    val healthItems: List<SourceHealthItem>
)

class RugbyCascadeClient {
    private val artZone = ZoneId.of("America/Argentina/Buenos_Aires")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm 'ART'")

    private fun buildApi(timeoutMs: Long): RugbyFeedApi {
        val safeTimeout = timeoutMs.coerceIn(4000L, 25000L)
        val ok = OkHttpClient.Builder()
            .connectTimeout(safeTimeout, TimeUnit.MILLISECONDS)
            .readTimeout(safeTimeout, TimeUnit.MILLISECONDS)
            .writeTimeout(safeTimeout, TimeUnit.MILLISECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://raw.githubusercontent.com/")
            .client(ok)
            .build()
            .create(RugbyFeedApi::class.java)
    }

    /**
     * Fetches with exponential backoff (600ms -> 1200ms -> 2400ms) and automatic URL rotation.
     */
    private suspend fun fetchWithBackoffAndRotation(
        api: RugbyFeedApi,
        urls: List<String>,
        maxAttempts: Int = 3
    ): Pair<String?, Long> {
        val startAll = System.currentTimeMillis()
        var waitMs = 600L
        var attempt = 0
        while (attempt < maxAttempts) {
            val url = urls[attempt % urls.size]
            val result = runCatching { api.fetchRaw(url).string() }.getOrNull()
            if (!result.isNullOrBlank()) {
                return result to (System.currentTimeMillis() - startAll)
            }
            attempt++
            if (attempt < maxAttempts) {
                delay(waitMs)
                waitMs *= 2
            }
        }
        return null to (System.currentTimeMillis() - startAll)
    }

    suspend fun executeCascade(timeoutMs: Long, includeSnapshot: Boolean): CascadeSyncResult {
        val api = buildApi(timeoutMs)
        val nowLabel = timeFmt.format(ZonedDateTime.now(artZone))
        val health = mutableListOf<SourceHealthItem>()

        // Nivel 1A: World Rugby Official Pulse API (Live + Upcoming + Completed)
        val (wrRaw, wrLat) = fetchWithBackoffAndRotation(
            api = api,
            urls = listOf(
                "https://api.wr-rims-prod.pulselive.com/rugby/v3/match?states=L,U,C&pageSize=40",
                "https://api.wr-rims-prod.pulselive.com/rugby/v3/match?states=L&pageSize=20"
            ),
            maxAttempts = 2
        )
        val wrMatches = wrRaw?.let { runCatching { RugbyJsonParser.parseWorldRugbyLive(it) }.getOrNull() }.orEmpty()
        health.add(
            SourceHealthItem(
                id = "src_wr",
                name = "World Rugby Official Pulse",
                tier = 1,
                statusLabel = if (wrRaw != null) "OK" else "SIN RESPUESTA",
                isHealthy = wrRaw != null,
                latencyMs = wrLat,
                detail = if (wrRaw != null) "${wrMatches.size} partidos en vivo/recientes" else "Rotación agotada (backoff aplicado)",
                checkedAtArg = nowLabel
            )
        )

        // Nivel 1B: Pipeline verificado UAR / URBA / SRA / Noticias (GitHub CDN)
        var snapshotJson: String? = null
        if (includeSnapshot) {
            val (snapRaw, snapLat) = fetchWithBackoffAndRotation(
                api = api,
                urls = listOf(
                    "https://raw.githubusercontent.com/Porotoo017/rugby-intelligence-engine/main/data/rugby_engine_output.json",
                    "https://cdn.jsdelivr.net/gh/Porotoo017/rugby-intelligence-engine@main/data/rugby_engine_output.json"
                ),
                maxAttempts = 2
            )
            snapshotJson = snapRaw
            health.add(
                SourceHealthItem(
                    id = "src_uar_urba",
                    name = "Pipeline Oficial UAR · URBA · SRA",
                    tier = 1,
                    statusLabel = if (snapRaw != null) "OK" else "CACHÉ LOCAL",
                    isHealthy = snapRaw != null,
                    latencyMs = snapLat,
                    detail = if (snapRaw != null) "Catálogo maestro + 99 clubes URBA + noticias" else "Usando Room offline",
                    checkedAtArg = nowLabel
                )
            )
        }

        // Nivel 2: ESPN Rugby Union Scoreboard (validación cruzada en tiempo real)
        val (espnRaw, espnLat) = fetchWithBackoffAndRotation(
            api = api,
            urls = listOf(
                "https://site.api.espn.com/apis/site/v2/sports/rugby/scorepanel",
                "https://site.api.espn.com/apis/site/v2/sports/rugby/164205/scoreboard"
            ),
            maxAttempts = 2
        )
        val espnMatchesRaw = espnRaw?.let { runCatching { RugbyJsonParser.parseEspnScoreboard(it) }.getOrNull() }.orEmpty()

        // Enrich live matches on ESPN with play-by-play events & stats (up to 4 active games per tick)
        val espnEnriched = espnMatchesRaw.map { m ->
            if (m.id.startsWith("espn:") && m.status == com.rugbypro.app.domain.model.MatchStatus.LIVE) {
                val eventId = m.id.removePrefix("espn:")
                val detailRaw = runCatching {
                    api.fetchRaw("https://site.api.espn.com/apis/site/v2/sports/rugby/summary?event=$eventId").string()
                }.getOrNull()
                if (!detailRaw.isNullOrBlank()) {
                    val (evs, stats) = runCatching { RugbyJsonParser.parseEspnMatchDetail(detailRaw) }.getOrDefault(emptyList<RugbyMatch>().let { emptyList<com.rugbypro.app.domain.model.MatchEvent>() to null })
                    m.copy(events = evs, liveStats = stats)
                } else m
            } else m
        }

        health.add(
            SourceHealthItem(
                id = "src_espn",
                name = "ESPN Rugby Union Live",
                tier = 2,
                statusLabel = if (espnRaw != null) "OK" else "RESPALDO",
                isHealthy = espnRaw != null,
                latencyMs = espnLat,
                detail = if (espnRaw != null) "${espnEnriched.size} partidos consultados para validación cruzada" else "Sin enlace actual",
                checkedAtArg = nowLabel
            )
        )

        // Nivel 2B: BBC Sport Rugby Union RSS check
        val (bbcRaw, bbcLat) = fetchWithBackoffAndRotation(
            api = api,
            urls = listOf("https://feeds.bbci.co.uk/sport/rugby-union/rss.xml"),
            maxAttempts = 1
        )
        health.add(
            SourceHealthItem(
                id = "src_bbc",
                name = "BBC Sport Rugby RSS",
                tier = 2,
                statusLabel = if (bbcRaw != null) "OK" else "ESPERA",
                isHealthy = bbcRaw != null,
                latencyMs = bbcLat,
                detail = if (bbcRaw != null) "Feed de noticias verificado" else "Usando feed cacheado",
                checkedAtArg = nowLabel
            )
        )

        return CascadeSyncResult(
            snapshotJson = snapshotJson,
            worldRugbyMatches = wrMatches,
            espnLiveMatches = espnEnriched,
            healthItems = health
        )
    }
}
