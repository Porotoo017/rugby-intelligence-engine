package com.rugbypro.app.data.remote

import com.rugbypro.app.domain.model.RugbyMatch
import com.rugbypro.app.domain.model.SourceHealthItem
import com.rugbypro.app.domain.usecase.CrossVerifyMatchesUseCase
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

interface DynamicRugbyApi {
    @GET
    suspend fun fetchRaw(@Url url: String): String
}

data class CascadeSyncResult(
    val snapshotJson: String?,
    val worldRugbyMatches: List<RugbyMatch>,
    val secondaryLiveMatches: List<RugbyMatch>,
    val healthItems: List<SourceHealthItem>
)

class RugbyCascadeClient {

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
            .addConverterFactory(ScalarsConverterFactory.create())
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
            var waitMs = 450L
            repeat(maxAttemptsPerUrl) {
                val res = runCatching { api.fetchRaw(url) }.getOrNull()
                if (!res.isNullOrBlank()) {
                    return res to (System.currentTimeMillis() - started)
                }
                delay(waitMs)
                waitMs = (waitMs * 2).coerceAtMost(2400L)
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

        // Nivel 1: Endpoints oficiales + pipeline consolidado (uso interno)
        val wrUrls = listOf(
            "https://api.wr-rims-prod.pulselive.com/rugby/v3/match?states=L,U,C&pageSize=60&sort=asc",
            "https://api.wr-rims-prod.pulselive.com/rugby/v3/match?states=L,U&pageSize=40&sort=asc"
        )
        val (wrRaw, wrLat) = fetchWithBackoffAndRotation(api, wrUrls)
        val wrMatches = wrRaw?.let { RugbyJsonParser.parseWorldRugbyMatches(it) }.orEmpty()
        health.add(
            SourceHealthItem(
                id = "tier1_official",
                name = "Official Feed",
                tier = 1,
                statusLabel = if (wrRaw != null) "OK" else "FAIL",
                isHealthy = wrRaw != null,
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

        // Nivel 2: Endpoints deportivos secundarios para validación cruzada interna
        val secondaryLeagues = listOf(
            "289234" to "Internacionales",
            "270557" to "Six Nations",
            "242041" to "Rugby Championship",
            "270559" to "Top 14",
            "267979" to "URC"
        )
        val secondaryCollected = mutableListOf<RugbyMatch>()
        val secStarted = System.currentTimeMillis()
        var secSuccessCount = 0

        for ((lid, lName) in secondaryLeagues) {
            val sbUrl = "https://site.api.espn.com/apis/site/v2/sports/rugby/$lid/scoreboard"
            val (sbRaw, _) = fetchWithBackoffAndRotation(api, listOf(sbUrl), maxAttemptsPerUrl = 1)
            if (sbRaw != null) {
                secSuccessCount++
                secondaryCollected.addAll(RugbyJsonParser.parseEspnScoreboard(sbRaw, lid, lName))
            }
        }

        // Si validación cruzada interna no coincide en algún partido activo, reintento silencioso
        if (wrMatches.isNotEmpty() && secondaryCollected.isNotEmpty()) {
            val check = CrossVerifyMatchesUseCase.execute(wrMatches, secondaryCollected)
            val needsRetry = check.any {
                it.status == com.rugbypro.app.domain.model.MatchStatus.LIVE &&
                    it.verificationStatus == com.rugbypro.app.domain.model.VerificationStatus.PENDING_VERIFICATION
            }
            if (needsRetry) {
                delay(450L)
                val (retryRaw, _) = fetchWithBackoffAndRotation(api, wrUrls, maxAttemptsPerUrl = 1)
                retryRaw?.let { refreshed ->
                    val parsedRetry = RugbyJsonParser.parseWorldRugbyMatches(refreshed)
                    if (parsedRetry.isNotEmpty()) {
                        secondaryCollected.addAll(parsedRetry)
                    }
                }
            }
        }

        // Enriquecer partidos en vivo con play-by-play y estadísticas en vivo
        val enrichedSecondary = mutableListOf<RugbyMatch>()
        for (m in secondaryCollected) {
            if (m.status == com.rugbypro.app.domain.model.MatchStatus.LIVE && enrichedSecondary.count { it.status == com.rugbypro.app.domain.model.MatchStatus.LIVE } < 4) {
                val evId = m.id.removePrefix("espn_")
                val sumUrl = "https://site.api.espn.com/apis/site/v2/sports/rugby/${m.leagueId}/summary?event=$evId"
                val (sumRaw, _) = fetchWithBackoffAndRotation(api, listOf(sumUrl), maxAttemptsPerUrl = 1)
                if (sumRaw != null) {
                    val (events, stats) = RugbyJsonParser.parseEspnSummaryDetails(sumRaw, m.homeName, m.awayName)
                    enrichedSecondary.add(m.copy(events = events, liveStats = stats))
                    continue
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

        val (rssRaw, rssLat) = fetchWithBackoffAndRotation(
            api,
            listOf("https://feeds.bbci.co.uk/sport/rugby-union/rss.xml"),
            maxAttemptsPerUrl = 1
        )
        health.add(
            SourceHealthItem(
                id = "tier2_news",
                name = "News Feed",
                tier = 2,
                statusLabel = if (rssRaw != null) "OK" else "CACHE",
                isHealthy = rssRaw != null,
                latencyMs = rssLat,
                detail = if (rssRaw != null) "OK" else "CACHE",
                checkedAtArg = nowLabel
            )
        )

        return CascadeSyncResult(
            snapshotJson = snapshotJson,
            worldRugbyMatches = wrMatches,
            secondaryLiveMatches = enrichedSecondary,
            healthItems = health
        )
    }
}
