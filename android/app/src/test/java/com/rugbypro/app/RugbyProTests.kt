package com.rugbypro.app

import com.rugbypro.app.data.remote.RugbyJsonParser
import com.rugbypro.app.domain.model.MatchEvent
import com.rugbypro.app.domain.model.MatchEventType
import com.rugbypro.app.domain.model.MatchStatus
import com.rugbypro.app.domain.model.RugbyMatch
import com.rugbypro.app.domain.model.TeamProfile
import com.rugbypro.app.domain.model.VerificationStatus
import com.rugbypro.app.domain.usecase.CompareTeamsUseCase
import com.rugbypro.app.domain.usecase.CrossVerifyMatchesUseCase
import com.rugbypro.app.domain.usecase.DetectKeyLiveEventsUseCase
import com.rugbypro.app.domain.usecase.FilterMatchesUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RugbyProTests {

    private fun sampleMatch(
        id: String = "m1",
        source: String = "world_rugby",
        home: String = "Argentina",
        away: String = "Scotland",
        status: MatchStatus = MatchStatus.FINISHED,
        homeScore: Int? = 28,
        awayScore: Int? = 21,
        dayArg: String = "Sáb 28 Nov",
        events: List<MatchEvent> = emptyList()
    ) = RugbyMatch(
        id = id,
        source = source,
        leagueId = "int",
        leagueName = "Test Match",
        leagueShort = "Test",
        phase = "",
        group = "nations",
        category = "Selecciones",
        status = status,
        minuteDisplay = if (status == MatchStatus.LIVE) "65'" else "",
        periodDisplay = status.label,
        dateArg = "$dayArg · 16:00 ART",
        dayArg = dayArg,
        timeArg = "16:00 ART",
        timestampSec = 1795892400L,
        venueName = "Murrayfield",
        venueCity = "Edinburgh",
        homeName = home,
        homeAbbr = home.take(3).uppercase(),
        homeCrestUrl = "",
        homeFlagUrl = "",
        homeCoach = "",
        homeRank = 6,
        homeScore = homeScore,
        awayName = away,
        awayAbbr = away.take(3).uppercase(),
        awayCrestUrl = "",
        awayFlagUrl = "",
        awayCoach = "",
        awayRank = 7,
        awayScore = awayScore,
        isArgentina = true,
        verificationStatus = VerificationStatus.PENDING_VERIFICATION,
        verifiedSources = listOf(source),
        events = events,
        liveStats = null
    )

    @Test
    fun `cross verification marks VERIFIED when two sources agree`() {
        val primary = listOf(sampleMatch(id = "wr:1", source = "world_rugby", homeScore = 24, awayScore = 17))
        val secondary = listOf(sampleMatch(id = "espn:1", source = "espn_live", homeScore = 24, awayScore = 17))
        val res = CrossVerifyMatchesUseCase.execute(primary, secondary)
        assertEquals(1, res.size)
        assertEquals(VerificationStatus.VERIFIED, res[0].verificationStatus)
        assertTrue(res[0].verifiedSources.containsAll(listOf("world_rugby", "espn_live")))
    }

    @Test
    fun `cross verification keeps PENDING when scores differ`() {
        val primary = listOf(sampleMatch(id = "wr:1", source = "world_rugby", homeScore = 24, awayScore = 17))
        val secondary = listOf(sampleMatch(id = "espn:1", source = "espn_live", homeScore = 24, awayScore = 20))
        val res = CrossVerifyMatchesUseCase.execute(primary, secondary)
        assertEquals(VerificationStatus.PENDING_VERIFICATION, res[0].verificationStatus)
    }

    @Test
    fun `detect key live events triggers try red card and fulltime alerts`() {
        val prev = listOf(sampleMatch(id = "m1", status = MatchStatus.LIVE, homeScore = 14, awayScore = 10))
        val newEvents = listOf(
            MatchEvent("e1", 58, "2T", MatchEventType.TRY, "home", "Carreras", "Try"),
            MatchEvent("e2", 62, "2T", MatchEventType.RED_CARD, "away", "Smith", "Tarjeta Roja")
        )
        val cur = listOf(sampleMatch(id = "m1", status = MatchStatus.FINISHED, homeScore = 21, awayScore = 10, events = newEvents))
        val alerts = DetectKeyLiveEventsUseCase.execute(prev, cur, notifyTries = true, notifyRedCards = true, notifyFullTime = true)
        assertEquals(3, alerts.size)
        assertTrue(alerts.any { it.channelType == "TRY" })
        assertTrue(alerts.any { it.channelType == "RED" })
        assertTrue(alerts.any { it.channelType == "FT" })
    }

    @Test
    fun `compare teams computes aggregate wins and points strictly from finished games`() {
        val tA = TeamProfile("arg", "Argentina", "", "", "Contepomi", "", 6, listOf("nations"), listOf("TRC"), "", emptyList())
        val tB = TeamProfile("sco", "Scotland", "", "", "Townsend", "", 7, listOf("nations"), listOf("6N"), "", emptyList())
        val matches = listOf(
            sampleMatch(id = "1", home = "Argentina", away = "Scotland", status = MatchStatus.FINISHED, homeScore = 30, awayScore = 20),
            sampleMatch(id = "2", home = "Argentina", away = "Scotland", status = MatchStatus.SCHEDULED, homeScore = null, awayScore = null)
        )
        val cmp = CompareTeamsUseCase.execute(tA, tB, matches)
        assertEquals(1, cmp.leftPlayed)
        assertEquals(1, cmp.leftWins)
        assertEquals(30, cmp.leftPointsFor)
        assertEquals(20, cmp.leftPointsAgainst)
        assertEquals(0, cmp.rightWins)
    }

    @Test
    fun `filter matches filters by status and query`() {
        val m1 = sampleMatch(id = "1", home = "Los Pumas", away = "All Blacks", status = MatchStatus.LIVE)
        val m2 = sampleMatch(id = "2", home = "SIC", away = "CASI", status = MatchStatus.SCHEDULED).copy(group = "urba", isArgentina = false)
        val res = FilterMatchesUseCase.execute(listOf(m1, m2), groupFilter = "urba", statusFilter = MatchStatus.SCHEDULED, query = "casi")
        assertEquals(1, res.size)
        assertEquals("2", res[0].id)
    }

    @Test
    fun `espn scoreboard parser handles live clock and score cleanly`() {
        val json = """
        {
          "leagues": [{"id": "164205", "name": "Rugby Championship", "abbreviation": "TRC"}],
          "events": [{
            "id": "999",
            "date": "2026-11-28T19:00:00Z",
            "competitions": [{
              "status": {"displayClock": "52:10", "period": 2, "type": {"state": "in", "shortDetail": "2T"}},
              "venue": {"fullName": "Vélez", "address": {"city": "Buenos Aires"}},
              "competitors": [
                {"homeAway": "home", "score": "19", "team": {"displayName": "Argentina", "abbreviation": "ARG", "logo": ""}},
                {"homeAway": "away", "score": "15", "team": {"displayName": "New Zealand", "abbreviation": "NZL", "logo": ""}}
              ]
            }]
          }]
        }
        """.trimIndent()
        val parsed = RugbyJsonParser.parseEspnScoreboard(json)
        assertEquals(1, parsed.size)
        assertEquals(MatchStatus.LIVE, parsed[0].status)
        assertEquals(19, parsed[0].homeScore)
        assertEquals(15, parsed[0].awayScore)
        assertEquals("52:10", parsed[0].minuteDisplay)
    }
}
