package com.rugbypro.app

import com.rugbypro.app.data.remote.RugbyJsonParser
import com.rugbypro.app.domain.model.ActiveKitRole
import com.rugbypro.app.domain.model.MatchEvent
import com.rugbypro.app.domain.model.MatchEventType
import com.rugbypro.app.domain.model.MatchStatus
import com.rugbypro.app.domain.model.PlayerProfile
import com.rugbypro.app.domain.model.RugbyMatch
import com.rugbypro.app.domain.model.TeamKit
import com.rugbypro.app.domain.model.TeamProfile
import com.rugbypro.app.data.remote.RugbyCascadeClient
import com.rugbypro.app.data.remote.RugbyJsonParser
import com.rugbypro.app.domain.model.MatchDayPart
import com.rugbypro.app.domain.model.ThermalZone
import com.rugbypro.app.domain.model.VerificationStatus
import com.rugbypro.app.domain.usecase.PreMatchWindowUseCase
import com.rugbypro.app.domain.usecase.RugbyDateUtils
import com.rugbypro.app.domain.usecase.WeatherClassificationUseCase
import com.rugbypro.app.domain.usecase.CompareTeamsUseCase
import com.rugbypro.app.domain.usecase.CrossVerifyMatchesUseCase
import com.rugbypro.app.domain.usecase.DetectKeyLiveEventsUseCase
import com.rugbypro.app.domain.usecase.FilterLiveAndTodayMatchesUseCase
import com.rugbypro.app.domain.usecase.FilterMatchesUseCase
import com.rugbypro.app.domain.usecase.ResolveMatchKitsUseCase
import com.rugbypro.app.domain.usecase.SearchTeamsAndPlayersUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RugbyProTests {

    private fun sampleMatch(
        id: String,
        source: String,
        status: MatchStatus,
        homeScore: Int?,
        awayScore: Int?,
        tsSec: Long = 1000L,
        dateArg: String = "26/11/2026 16:00",
        events: List<MatchEvent> = emptyList()
    ) = RugbyMatch(
        id = id,
        source = source,
        leagueId = "289234",
        leagueName = "Test Internationals",
        leagueShort = "INT",
        phase = "",
        group = "nations",
        category = "Selecciones",
        status = status,
        minuteDisplay = "42'",
        periodDisplay = "2do Tiempo",
        dateArg = dateArg,
        dayArg = dateArg.take(5),
        timeArg = "16:00",
        timestampSec = tsSec,
        venueName = "Vélez",
        venueCity = "Buenos Aires",
        homeName = "Argentina",
        homeAbbr = "ARG",
        homeCrestUrl = "",
        homeFlagUrl = "",
        homeCoach = "Felipe Contepomi",
        homeRank = 6,
        homeScore = homeScore,
        awayName = "France",
        awayAbbr = "FRA",
        awayCrestUrl = "",
        awayFlagUrl = "",
        awayCoach = "Fabien Galthié",
        awayRank = 4,
        awayScore = awayScore,
        isArgentina = true,
        verificationStatus = VerificationStatus.PENDING_VERIFICATION,
        verifiedSources = listOf(source),
        events = events,
        liveStats = null
    )

    @Test
    fun `cross verification marks VERIFIED when two sources agree`() {
        val m1 = sampleMatch("a", "official_live", MatchStatus.LIVE, 17, 14)
        val m2 = sampleMatch("b", "sec_live", MatchStatus.LIVE, 17, 14)
        val res = CrossVerifyMatchesUseCase.execute(listOf(m1), listOf(m2))
        assertEquals(1, res.size)
        assertEquals(VerificationStatus.VERIFIED, res.first().verificationStatus)
    }

    @Test
    fun `live filter strictly excludes finished matches`() {
        val nowMs = System.currentTimeMillis()
        val nowSec = nowMs / 1000L
        val live = sampleMatch("1", "s", MatchStatus.LIVE, 10, 7, tsSec = nowSec)
        val todayUpcoming = sampleMatch("2", "s", MatchStatus.SCHEDULED, null, null, tsSec = nowSec + 3600L, dateArg = "")
        val finished = sampleMatch("3", "s", MatchStatus.FINISHED, 24, 17, tsSec = nowSec)
        val all = listOf(live, todayUpcoming, finished)

        val liveOnly = FilterLiveAndTodayMatchesUseCase.liveNow(all)
        val todayOnly = FilterLiveAndTodayMatchesUseCase.upcomingToday(all, nowMs = nowMs)

        assertEquals(listOf("1"), liveOnly.map { it.id })
        assertEquals(listOf("2"), todayOnly.map { it.id })
        assertTrue((liveOnly + todayOnly).none { it.status == MatchStatus.FINISHED })
    }

    @Test
    fun `resolve match kits selects away kit on primary clash and unknown when missing`() {
        val blueHome = TeamProfile(
            key = "arg", name = "Argentina", crestUrl = "", flagUrl = "", coach = "", stadium = "",
            worldRank = 6, groups = emptyList(), leagues = emptyList(), division = "", roster = emptyList(),
            homeKit = TeamKit(true, "#0050BA", "#FFFFFF", "#FFFFFF", "hoops", "Titular"),
            awayKit = TeamKit(true, "#0B1F3A", "#FFD700", "#0B1F3A", "solid", "Suplente")
        )
        val blueAwayClash = TeamProfile(
            key = "ita", name = "Italy", crestUrl = "", flagUrl = "", coach = "", stadium = "",
            worldRank = 8, groups = emptyList(), leagues = emptyList(), division = "", roster = emptyList(),
            homeKit = TeamKit(true, "#0055BF", "#FFFFFF", "#FFFFFF", "solid", "Titular"),
            awayKit = TeamKit(true, "#E2E2E2", "#0050BA", "#0050BA", "solid", "Suplente")
        )
        val resolved = ResolveMatchKitsUseCase.resolveForMatch(blueHome, blueAwayClash)
        assertEquals(ActiveKitRole.HOME, resolved.homeRole)
        assertEquals(ActiveKitRole.AWAY, resolved.awayRole)
        assertEquals("#E2E2E2", resolved.awayKit?.primaryHex)

        val unverified = ResolveMatchKitsUseCase.resolveForMatch(null, null)
        assertEquals(ActiveKitRole.UNKNOWN, unverified.homeRole)
        assertNull(unverified.homeKit)
    }

    @Test
    fun `search players filters by query country team and position`() {
        val p1 = PlayerProfile("10", "Santiago Carreras", "Apertura", "FH", true, false, 28, "181", "85", 50, nationality = "Argentina")
        val p2 = PlayerProfile("1", "Danilo Fischetti", "Pilar", "PR", true, false, 28, "181", "113", 69, nationality = "Italy")
        val teams = listOf(
            TeamProfile("arg", "Argentina", "", "", "", "", 6, listOf("nations"), emptyList(), "", listOf(p1)),
            TeamProfile("ita", "Italy", "", "", "", "", 8, listOf("nations"), emptyList(), "", listOf(p2))
        )
        val res = SearchTeamsAndPlayersUseCase.searchPlayers(
            teams = teams,
            query = "carreras",
            countryFilter = "Argentina",
            teamFilter = "Argentina",
            positionFilter = "Apertura"
        )
        assertEquals(1, res.size)
        assertEquals("Santiago Carreras", res.first().player.name)
    }

    @Test
    fun `compare teams calculates wins and points correctly`() {
        val m = sampleMatch("1", "s", MatchStatus.FINISHED, 31, 20)
        val t1 = TeamProfile("arg", "Argentina", "", "", "", "", 6, emptyList(), emptyList(), "", emptyList())
        val t2 = TeamProfile("fra", "France", "", "", "", "", 4, emptyList(), emptyList(), "", emptyList())
        val comp = CompareTeamsUseCase.execute(t1, t2, listOf(m))
        assertEquals(1, comp.leftWins)
        assertEquals(0, comp.rightWins)
        assertEquals(31, comp.leftPointsFor)
    }

    @Test
    fun `detect key live events triggers alerts only once per new event`() {
        val evTry = MatchEvent("t1", 12, "1T", MatchEventType.TRY, "home", "Carreras", "Try")
        val prev = sampleMatch("1", "s", MatchStatus.LIVE, 0, 0)
        val cur = sampleMatch("1", "s", MatchStatus.LIVE, 5, 0, events = listOf(evTry))
        val alerts = DetectKeyLiveEventsUseCase.execute(listOf(prev), listOf(cur), true, true, true)
        assertEquals(1, alerts.size)
        assertEquals("TRY", alerts.first().channelType)
    }

    @Test
    fun `filter matches supports team query and date query`() {
        val m1 = sampleMatch("1", "s", MatchStatus.FINISHED, 20, 10, dateArg = "26/11/2026 16:00")
        val m2 = sampleMatch("2", "s", MatchStatus.FINISHED, 15, 12, dateArg = "10/10/2026 18:00")
        val out = FilterMatchesUseCase.execute(listOf(m1, m2), "todos", MatchStatus.FINISHED, "argentina", "26/11")
        assertEquals(listOf("1"), out.map { it.id })
    }

    @Test
    fun `espn scoreboard parser maps live event cleanly`() {
        val json = """
            {"leagues":[{"id":"289234","name":"Internationals","abbreviation":"INT"}],
             "events":[{"id":"999","date":"2026-11-26T18:00:00Z",
              "competitions":[{"status":{"displayClock":"55'","period":2,"type":{"state":"in","shortDetail":"2nd"}},
               "competitors":[
                 {"homeAway":"home","score":"21","team":{"displayName":"Argentina","abbreviation":"ARG","logo":""}},
                 {"homeAway":"away","score":"18","team":{"displayName":"Australia","abbreviation":"AUS","logo":""}}
               ]}]}]}
        """.trimIndent()
        val parsed = RugbyJsonParser.parseEspnScoreboard(json, "289234", "Internationals")
        assertEquals(1, parsed.size)
        assertEquals(MatchStatus.LIVE, parsed.first().status)
        assertEquals(21, parsed.first().homeScore)
    }

    @Test
    fun `weather parser extracts open meteo forecast and thermal classification`() {
        val json = """
            {
              "current": {
                "temperature_2m": 11.4,
                "apparent_temperature": 8.9,
                "relative_humidity_2m": 82,
                "weather_code": 61,
                "wind_speed_10m": 24.5,
                "wind_direction_10m": 180.0
              },
              "hourly": {
                "precipitation_probability": [75]
              }
            }
        """.trimIndent()
        val w = RugbyCascadeClient.parseOpenMeteoForecast(json, MatchDayPart.TARDE)
        assertEquals(true, w?.isAvailable)
        assertEquals(ThermalZone.FRIO, w?.thermalZone)
        assertEquals("Lluvia", w?.conditionLabel)
        assertEquals("S", w?.windDirection)
        assertEquals(75, w?.rainProbabilityPct)
        assertEquals(82, w?.humidityPct)
        assertEquals(MatchDayPart.TARDE, w?.dayPart)
    }

    @Test
    fun `prematch window computes exact 30 minute trigger before kickoff`() {
        val nowMs = 1_700_000_000_000L
        val kickoffSec = (nowMs / 1000L) + 3600L // en 60 minutos
        val m = sampleMatch("pm1", "s", MatchStatus.SCHEDULED, null, null, tsSec = kickoffSec)
        val targets = PreMatchWindowUseCase.computeTargets(listOf(m), nowMs = nowMs)
        assertEquals(1, targets.size)
        assertEquals((kickoffSec * 1000L) - 30L * 60L * 1000L, targets.first().triggerAtMs)
        val inWindow = sampleMatch("pm2", "s", MatchStatus.SCHEDULED, null, null, tsSec = (nowMs / 1000L) + 900L)
        assertTrue(PreMatchWindowUseCase.isInsideThirtyMinuteWindow(inWindow, nowMs = nowMs))
    }

    @Test
    fun `weather thermal zones and date utils format cleanly`() {
        assertEquals(ThermalZone.FRIO, WeatherClassificationUseCase.thermalZoneFor(9.0))
        assertEquals(ThermalZone.TEMPLADO, WeatherClassificationUseCase.thermalZoneFor(18.5))
        assertEquals(ThermalZone.CALOR, WeatherClassificationUseCase.thermalZoneFor(29.0))
        assertEquals("58.3%", RugbyDateUtils.formatPercent(58.34))
        assertEquals(MatchDayPart.NOCHE, RugbyDateUtils.inferDayPart(0L, "21:15"))
    }

    @Test
    fun `date utils formats and normalizes all dates to dd_MM_yyyy`() {
        assertEquals("26/11/2026 · 17:10", RugbyDateUtils.normalizeDisplayDate("Sáb 26 Nov · 17:10 ART"))
        assertEquals("26/11/2026", RugbyDateUtils.normalizeDisplayDate("2026-11-26"))
        val formatted = RugbyDateUtils.formatLocalDateOnly(1_764_180_000L, "")
        assertTrue(Regex("^\\d{2}/\\d{2}/\\d{4}$").matches(formatted))
    }

    @Test
    fun `home team falls back to away kit when home kit is missing`() {
        val onlyAway = TeamProfile(
            key = "t1", name = "Test Club", crestUrl = "", flagUrl = "", coach = "", stadium = "",
            worldRank = null, groups = emptyList(), leagues = emptyList(), division = "", roster = emptyList(),
            homeKit = null,
            awayKit = TeamKit(true, "#112233", "#FFD700", "#112233", "solid", "Suplente")
        )
        val resolved = ResolveMatchKitsUseCase.resolveForMatch(onlyAway, null)
        assertEquals(ActiveKitRole.AWAY, resolved.homeRole)
        assertEquals("#112233", resolved.homeKit?.primaryHex)
    }

    @Test
    fun `world rugby match detail parses referee officials and lineups`() {
        val json = """
            {
              "officials": [
                {"position": "Referee", "official": {"name": {"display": "Hollie Davidson"}, "country": "Scotland"}},
                {"position": "TMO", "official": {"name": {"display": "Mike Adamson"}, "country": "Scotland"}}
              ],
              "teams": [
                {"teamList": {"captainIds": ["1"], "list": [{"number": "10", "positionLabel": "Apertura", "position": "FH", "player": {"id": "1", "name": {"display": "Santiago Carreras"}}}]}},
                {"teamList": {"captainIds": [], "list": [{"number": "9", "positionLabel": "Medio Scrum", "position": "SH", "player": {"id": "2", "name": {"display": "Antoine Dupont"}}}]}}
              ]
            }
        """.trimIndent()
        val det = RugbyJsonParser.parseWorldRugbyMatchDetail(json)
        assertEquals("Hollie Davidson (Scotland)", det.refereeName)
        assertEquals(2, det.officials.size)
        assertEquals("Árbitro principal", det.officials.first().role)
        assertEquals("Santiago Carreras", det.homeLineup.first().name)
        assertTrue(det.homeLineup.first().captain)
        assertEquals("Antoine Dupont", det.awayLineup.first().name)
    }
}
