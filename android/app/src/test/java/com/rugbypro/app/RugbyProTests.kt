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
import com.rugbypro.app.domain.model.VerificationStatus
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
}
