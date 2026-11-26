package com.rugbypro.app.data.remote

import com.rugbypro.app.domain.model.CompetitionItem
import com.rugbypro.app.domain.model.LiveMatchStats
import com.rugbypro.app.domain.model.MatchEvent
import com.rugbypro.app.domain.model.MatchEventType
import com.rugbypro.app.domain.model.MatchStatus
import com.rugbypro.app.domain.model.NewsArticle
import com.rugbypro.app.domain.model.PlayerProfile
import com.rugbypro.app.domain.model.RugbyDashboard
import com.rugbypro.app.domain.model.RugbyMatch
import com.rugbypro.app.domain.model.StandingRow
import com.rugbypro.app.domain.model.TeamKit
import com.rugbypro.app.domain.model.TeamProfile
import com.rugbypro.app.domain.model.TournamentStandings
import com.rugbypro.app.domain.model.VerificationStatus
import com.rugbypro.app.domain.model.WorldRankingRow
import com.rugbypro.app.domain.usecase.CrossVerifyMatchesUseCase
import com.rugbypro.app.domain.usecase.ResolveMatchKitsUseCase
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object RugbyJsonParser {

    private val artZone: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
    private val argFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun parseEngineSnapshot(jsonStr: String, savedNewsIds: Set<String> = emptySet()): RugbyDashboard {
        val root = JSONObject(jsonStr)
        val generatedAtArg = root.optString("generatedAtArg", "")

        val argHub = root.optJSONObject("argentinaHub")
        val coaches = argHub?.optJSONObject("coaches")
        val coachXV = coaches?.optJSONObject("xv")?.optString("name", "Felipe Contepomi") ?: "Felipe Contepomi"
        val coach7s = coaches?.optJSONObject("sevens")?.optString("name", "Santiago Gómez Cora") ?: "Santiago Gómez Cora"

        val comps = mutableListOf<CompetitionItem>()
        val compArr = root.optJSONArray("competitions") ?: JSONArray()
        for (i in 0 until compArr.length()) {
            val c = compArr.optJSONObject(i) ?: continue
            comps.add(
                CompetitionItem(
                    id = c.optString("id"),
                    name = c.optString("name"),
                    region = c.optString("region"),
                    category = c.optString("category"),
                    teamsCount = c.optInt("teamsCount", 0),
                    season = c.optString("season", "")
                )
            )
        }

        val matches = mutableListOf<RugbyMatch>()
        val matchesArr = root.optJSONArray("matches") ?: JSONArray()
        for (i in 0 until matchesArr.length()) {
            matches.add(parseSnapshotMatch(matchesArr.optJSONObject(i) ?: continue))
        }

        val nextXV = root.optJSONObject("argentinaNextXV")?.let { parseSnapshotMatch(it) }
        val next7s = root.optJSONObject("argentinaNext7s")?.let { parseSnapshotMatch(it) }

        val teamsMap = linkedMapOf<String, TeamProfile>()
        val dirArr = root.optJSONArray("teamsDirectory") ?: JSONArray()
        for (i in 0 until dirArr.length()) {
            val t = dirArr.optJSONObject(i) ?: continue
            val profile = parseTeamProfile(t)
            if (profile.name.isNotBlank()) {
                teamsMap[profile.key.ifBlank { profile.name.lowercase() }] = profile
            }
        }

        val urbaObj = root.optJSONObject("urba")
        val urbaClubs = urbaObj?.optJSONArray("clubs") ?: JSONArray()
        for (i in 0 until urbaClubs.length()) {
            val uc = urbaClubs.optJSONObject(i) ?: continue
            val name = uc.optString("name").trim()
            if (name.isBlank()) continue
            val key = name.lowercase()
            if (!teamsMap.containsKey(key)) {
                teamsMap[key] = TeamProfile(
                    key = key,
                    name = name,
                    crestUrl = uc.optString("crestUrl"),
                    flagUrl = "https://flagcdn.com/w80/ar.png",
                    coach = "",
                    stadium = listOf(uc.optString("cancha"), uc.optString("sede")).filter { it.isNotBlank() }.joinToString(" · "),
                    worldRank = null,
                    groups = listOf("urba"),
                    leagues = listOf("URBA ${uc.optString("division", "")}".trim()),
                    division = uc.optString("division", ""),
                    roster = emptyList(),
                    homeKit = parseTeamKit(uc.optJSONObject("homeKit")),
                    awayKit = parseTeamKit(uc.optJSONObject("awayKit"))
                )
            }
        }

        // Enriquecer estadísticas históricas reales de cada equipo a partir de partidos cerrados + historial
        val teamStats = mutableMapOf<String, IntArray>() // [played, wins, draws, losses]
        fun recordResult(teamName: String, ptsFor: Int, ptsAgainst: Int) {
            val k = CrossVerifyMatchesUseCase.normalizeTeam(teamName)
            if (k.isBlank()) return
            val arr = teamStats.getOrPut(k) { IntArray(4) }
            arr[0] += 1
            when {
                ptsFor > ptsAgainst -> arr[1] += 1
                ptsFor == ptsAgainst -> arr[2] += 1
                else -> arr[3] += 1
            }
        }
        val countedMatchKeys = mutableSetOf<String>()
        for (m in matches) {
            if (m.status == MatchStatus.FINISHED && m.homeScore != null && m.awayScore != null) {
                countedMatchKeys.add(CrossVerifyMatchesUseCase.matchSignature(m))
                recordResult(m.homeName, m.homeScore, m.awayScore)
                recordResult(m.awayName, m.awayScore, m.homeScore)
            }
        }
        val histArr = root.optJSONArray("history") ?: JSONArray()
        for (i in 0 until histArr.length()) {
            val h = histArr.optJSONObject(i) ?: continue
            if (h.isNull("homeScore") || h.isNull("awayScore")) continue
            val hn = h.optString("homeTeam")
            val an = h.optString("awayTeam")
            val day = h.optString("dateArg").take(10)
            val sig = "${CrossVerifyMatchesUseCase.normalizeTeam(hn)}_vs_${CrossVerifyMatchesUseCase.normalizeTeam(an)}_$day"
            if (sig !in countedMatchKeys) {
                countedMatchKeys.add(sig)
                val hs = h.optInt("homeScore")
                val as_ = h.optInt("awayScore")
                recordResult(hn, hs, as_)
                recordResult(an, as_, hs)
            }
        }

        val enrichedTeams = teamsMap.values.map { team ->
            val st = teamStats[CrossVerifyMatchesUseCase.normalizeTeam(team.name)]
            if (st != null && st[0] > 0) {
                team.copy(
                    historicalPlayed = st[0],
                    historicalWins = st[1],
                    historicalDraws = st[2],
                    historicalLosses = st[3]
                )
            } else {
                team
            }
        }

        val teamByNorm = enrichedTeams.associateBy { CrossVerifyMatchesUseCase.normalizeTeam(it.name) }

        val standingsList = mutableListOf<TournamentStandings>()
        val urbaChamps = urbaObj?.optJSONArray("championships") ?: JSONArray()
        for (i in 0 until urbaChamps.length()) {
            val ch = urbaChamps.optJSONObject(i) ?: continue
            val rows = parseStandingsRows(ch.optJSONArray("standings"), teamByNorm, defaultFlag = "https://flagcdn.com/w80/ar.png")
            if (rows.isNotEmpty()) {
                standingsList.add(
                    TournamentStandings(
                        id = "urba_${ch.optString("id")}",
                        name = ch.optString("name"),
                        shortName = ch.optString("short", ch.optString("name")),
                        group = "urba",
                        rows = rows
                    )
                )
            }
        }
        val sraObj = root.optJSONObject("sra")
        val sraRows = parseStandingsRows(sraObj?.optJSONArray("standings"), teamByNorm)
        if (sraRows.isNotEmpty()) {
            standingsList.add(
                TournamentStandings(
                    id = "sra",
                    name = "Súper Rugby Américas",
                    shortName = "SRA",
                    group = "sra",
                    rows = sraRows
                )
            )
        }

        val rankingsObj = root.optJSONObject("rankings")
        val rankingsMen = parseRankings(rankingsObj?.optJSONArray("men"), isWomen = false)
        val rankingsWomen = parseRankings(rankingsObj?.optJSONArray("women"), isWomen = true)

        val newsList = mutableListOf<NewsArticle>()
        val newsArr = root.optJSONArray("news") ?: JSONArray()
        for (i in 0 until newsArr.length()) {
            val n = newsArr.optJSONObject(i) ?: continue
            val id = n.optString("id", "news_$i")
            newsList.add(
                NewsArticle(
                    id = id,
                    title = n.optString("title"),
                    summary = n.optString("summary"),
                    source = n.optString("source"),
                    url = n.optString("url"),
                    imageUrl = n.optString("imageUrl"),
                    category = n.optString("topicLabel", n.optString("category", "General")).ifBlank { "General" },
                    publishedLabel = n.optString("dateArg", n.optString("publishedArg", n.optString("published", ""))),
                    savedOffline = id in savedNewsIds
                )
            )
        }

        val matchesWithKits = attachTeamKitsToMatches(matches, enrichedTeams)
        val nextXVWithKit = nextXV?.let { attachTeamKitsToMatches(listOf(it), enrichedTeams).firstOrNull() }
        val next7sWithKit = next7s?.let { attachTeamKitsToMatches(listOf(it), enrichedTeams).firstOrNull() }

        return RugbyDashboard(
            generatedAtArg = generatedAtArg,
            nextArgentinaXV = nextXVWithKit,
            nextArgentina7s = next7sWithKit,
            coachXV = coachXV,
            coach7s = coach7s,
            matches = matchesWithKits,
            competitions = comps,
            standings = standingsList,
            teams = enrichedTeams,
            rankingsMen = rankingsMen,
            rankingsWomen = rankingsWomen,
            news = newsList,
            sourcesHealth = emptyList(),
            isOfflineFallback = false
        )
    }

    fun attachTeamKitsToMatches(
        matches: List<RugbyMatch>,
        teams: List<TeamProfile>
    ): List<RugbyMatch> {
        if (teams.isEmpty()) return matches
        val byNorm = teams.associateBy { CrossVerifyMatchesUseCase.normalizeTeam(it.name) }
        return matches.map { m ->
            val hTeam = byNorm[CrossVerifyMatchesUseCase.normalizeTeam(m.homeName)]
            val aTeam = byNorm[CrossVerifyMatchesUseCase.normalizeTeam(m.awayName)]
            val resolved = ResolveMatchKitsUseCase.resolveForMatch(hTeam, aTeam)
            m.copy(
                homeCrestUrl = m.homeCrestUrl.ifBlank { hTeam?.crestUrl.orEmpty() },
                homeFlagUrl = m.homeFlagUrl.ifBlank { hTeam?.flagUrl.orEmpty() },
                awayCrestUrl = m.awayCrestUrl.ifBlank { aTeam?.crestUrl.orEmpty() },
                awayFlagUrl = m.awayFlagUrl.ifBlank { aTeam?.flagUrl.orEmpty() },
                homeKit = resolved.homeKit,
                awayKit = resolved.awayKit,
                homeActiveKitRole = resolved.homeRole,
                awayActiveKitRole = resolved.awayRole
            )
        }
    }

    private fun parseTeamKit(obj: JSONObject?): TeamKit? {
        if (obj == null) return null
        val primary = obj.optString("primaryHex", "").trim()
        if (!obj.optBoolean("verified", false) || primary.isBlank()) return null
        return TeamKit(
            verified = true,
            primaryHex = primary,
            secondaryHex = obj.optString("secondaryHex", "#FFFFFF").ifBlank { "#FFFFFF" },
            shortsHex = obj.optString("shortsHex", primary).ifBlank { primary },
            pattern = obj.optString("pattern", "solid").ifBlank { "solid" },
            label = obj.optString("label", "")
        )
    }

    private fun parseSnapshotMatch(m: JSONObject): RugbyMatch {
        val rawSt = m.optString("status").lowercase()
        val rawNote = m.optString("statusNote", m.optString("statusText", ""))
        val status = when {
            rawSt in listOf("live", "in") -> MatchStatus.LIVE
            rawSt in listOf("finished", "post") -> MatchStatus.FINISHED
            rawSt in listOf("postponed", "ppd", "delayed") || "posterg" in rawNote.lowercase() -> MatchStatus.POSTPONED
            rawSt in listOf("cancelled", "canceled", "canc") || "cancel" in rawNote.lowercase() -> MatchStatus.CANCELLED
            else -> MatchStatus.SCHEDULED
        }
        val home = m.optJSONObject("home") ?: JSONObject()
        val away = m.optJSONObject("away") ?: JSONObject()
        val venue = m.optJSONObject("venue") ?: JSONObject()

        val events = mutableListOf<MatchEvent>()
        val scoringArr = m.optJSONArray("scoring") ?: JSONArray()
        for (i in 0 until scoringArr.length()) {
            val ev = scoringArr.optJSONObject(i) ?: continue
            val rawType = ev.optString("type").lowercase()
            val evType = when {
                "try" in rawType -> MatchEventType.TRY
                "conv" in rawType -> MatchEventType.CONVERSION
                "pen" in rawType -> MatchEventType.PENALTY
                "drop" in rawType -> MatchEventType.DROP_GOAL
                else -> MatchEventType.TRY
            }
            events.add(
                MatchEvent(
                    id = "sc_$i",
                    minute = ev.optString("minute", "0").replace("'", "").trim().toIntOrNull() ?: ev.optInt("minute", 0),
                    period = ev.optString("period", ""),
                    type = evType,
                    teamSide = ev.optString("team", ev.optString("side", "home")),
                    player = ev.optString("player", ""),
                    detail = ev.optString("label", evType.label)
                )
            )
        }
        val cardsArr = m.optJSONArray("cards") ?: JSONArray()
        for (i in 0 until cardsArr.length()) {
            val c = cardsArr.optJSONObject(i) ?: continue
            val rawCol = c.optString("type", c.optString("color", "")).lowercase()
            val isRed = rawCol.contains("red") || rawCol.contains("roj")
            events.add(
                MatchEvent(
                    id = "cd_$i",
                    minute = c.optString("minute", "0").replace("'", "").trim().toIntOrNull() ?: c.optInt("minute", 0),
                    period = "",
                    type = if (isRed) MatchEventType.RED_CARD else MatchEventType.YELLOW_CARD,
                    teamSide = c.optString("team", c.optString("side", "home")),
                    player = c.optString("player", ""),
                    detail = if (isRed) "Tarjeta Roja" else "Tarjeta Amarilla"
                )
            )
        }

        val hScore = if (home.isNull("score")) null else home.optInt("score")
        val aScore = if (away.isNull("score")) null else away.optInt("score")
        val hRank = if (home.isNull("worldRank")) null else home.optInt("worldRank")
        val aRank = if (away.isNull("worldRank")) null else away.optInt("worldRank")
        val src = m.optString("source", "engine")

        return RugbyMatch(
            id = m.optString("id"),
            source = src,
            leagueId = m.optString("leagueId"),
            leagueName = m.optString("leagueName"),
            leagueShort = m.optString("leagueShort", m.optString("leagueName")),
            phase = m.optString("phase", ""),
            group = m.optString("group", ""),
            category = m.optString("category", ""),
            status = status,
            minuteDisplay = m.optString("statusText", ""),
            periodDisplay = when (status) {
                MatchStatus.LIVE -> m.optString("statusText", "EN VIVO")
                MatchStatus.FINISHED -> "Final"
                MatchStatus.POSTPONED -> "Postergado"
                MatchStatus.CANCELLED -> "Cancelado"
                MatchStatus.SCHEDULED -> "Programado"
            },
            dateArg = m.optString("dateArg", ""),
            dayArg = m.optString("dayArg", ""),
            timeArg = m.optString("timeArg", ""),
            timestampSec = m.optLong("ts", 0L),
            venueName = venue.optString("name", ""),
            venueCity = venue.optString("city", ""),
            homeName = home.optString("name", ""),
            homeAbbr = home.optString("abbr", ""),
            homeCrestUrl = home.optString("crestUrl", ""),
            homeFlagUrl = home.optString("flagUrl", ""),
            homeCoach = home.optString("coach", ""),
            homeRank = hRank,
            homeScore = hScore,
            awayName = away.optString("name", ""),
            awayAbbr = away.optString("abbr", ""),
            awayCrestUrl = away.optString("crestUrl", ""),
            awayFlagUrl = away.optString("flagUrl", ""),
            awayCoach = away.optString("coach", ""),
            awayRank = aRank,
            awayScore = aScore,
            isArgentina = m.optBoolean("isArgentina", false),
            verificationStatus = VerificationStatus.LOCAL_CACHE,
            verifiedSources = listOf(src),
            events = events.sortedBy { it.minute },
            liveStats = null,
            statusNote = rawNote.takeIf { it.isNotBlank() && (status == MatchStatus.POSTPONED || status == MatchStatus.CANCELLED) },
            isExtraTime = m.optBoolean("extraTime", false) || rawNote.lowercase().let { "extra" in it || "prorroga" in it || "prórroga" in it }
        )
    }

    private fun parseStandingsRows(
        arr: JSONArray?,
        teamByNorm: Map<String, TeamProfile>,
        defaultFlag: String = ""
    ): List<StandingRow> {
        if (arr == null) return emptyList()
        val out = mutableListOf<StandingRow>()
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            val tName = r.optString("team", r.optString("name"))
            val matchedTeam = teamByNorm[CrossVerifyMatchesUseCase.normalizeTeam(tName)]
            out.add(
                StandingRow(
                    pos = r.optInt("pos", i + 1),
                    teamName = tName,
                    crestUrl = r.optString("crestUrl").ifBlank { matchedTeam?.crestUrl.orEmpty() },
                    flagUrl = r.optString("flagUrl").ifBlank { matchedTeam?.flagUrl.orEmpty() }.ifBlank { defaultFlag },
                    played = r.optInt("played", r.optInt("pj", 0)),
                    won = r.optInt("won", r.optInt("pg", 0)),
                    drawn = r.optInt("drawn", r.optInt("pe", 0)),
                    lost = r.optInt("lost", r.optInt("pp", 0)),
                    pf = r.optInt("pf", 0),
                    pc = r.optInt("pc", 0),
                    diff = r.optInt("diff", 0),
                    bonus = r.optInt("bonus", 0),
                    pts = r.optInt("pts", 0)
                )
            )
        }
        return out
    }

    private fun parseTeamProfile(t: JSONObject): TeamProfile {
        val groups = mutableListOf<String>()
        val gArr = t.optJSONArray("groups") ?: JSONArray()
        for (i in 0 until gArr.length()) groups.add(gArr.optString(i))

        val leagues = mutableListOf<String>()
        val lArr = t.optJSONArray("leagues") ?: JSONArray()
        for (i in 0 until lArr.length()) leagues.add(lArr.optString(i))

        val roster = mutableListOf<PlayerProfile>()
        val rArr = t.optJSONArray("roster") ?: JSONArray()
        for (i in 0 until rArr.length()) {
            val p = rArr.optJSONObject(i) ?: continue
            val rawHeight = if (p.isNull("height")) null else p.optString("height").trim().takeIf { it.isNotBlank() && it != "null" }
            val rawWeight = if (p.isNull("weight")) null else p.optString("weight").trim().takeIf { it.isNotBlank() && it != "null" }
            roster.add(
                PlayerProfile(
                    jersey = p.optString("jersey", "-"),
                    name = p.optString("name", ""),
                    position = p.optString("position", ""),
                    posCode = p.optString("posCode", ""),
                    starter = p.optBoolean("starter", false),
                    captain = p.optBoolean("captain", false),
                    age = if (p.isNull("age")) null else p.optInt("age"),
                    height = rawHeight,
                    weight = rawWeight,
                    caps = if (p.isNull("caps")) null else p.optInt("caps"),
                    photoUrl = if (p.isNull("photoUrl")) null else p.optString("photoUrl").takeIf { it.isNotBlank() },
                    nationality = if (p.isNull("nationality")) null else p.optString("nationality").takeIf { it.isNotBlank() },
                    nationalityFlagUrl = if (p.isNull("nationalityFlagUrl")) null else p.optString("nationalityFlagUrl").takeIf { it.isNotBlank() },
                    tries = if (p.isNull("tries")) null else p.optInt("tries"),
                    points = if (p.isNull("points")) null else p.optInt("points"),
                    tackles = if (p.isNull("tackles")) null else p.optInt("tackles"),
                    extraInfo = if (p.isNull("extraInfo")) null else p.optString("extraInfo").takeIf { it.isNotBlank() }
                )
            )
        }
        return TeamProfile(
            key = t.optString("key"),
            name = t.optString("name"),
            crestUrl = t.optString("crestUrl"),
            flagUrl = t.optString("flagUrl"),
            coach = t.optString("coach"),
            stadium = t.optString("stadium"),
            worldRank = if (t.isNull("worldRank")) null else t.optInt("worldRank"),
            groups = groups,
            leagues = leagues,
            division = t.optString("division", ""),
            roster = roster,
            homeKit = parseTeamKit(t.optJSONObject("homeKit")),
            awayKit = parseTeamKit(t.optJSONObject("awayKit"))
        )
    }

    private fun parseRankings(arr: JSONArray?, isWomen: Boolean): List<WorldRankingRow> {
        if (arr == null) return emptyList()
        val out = mutableListOf<WorldRankingRow>()
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            out.add(
                WorldRankingRow(
                    pos = r.optInt("pos", i + 1),
                    prevPos = r.optInt("prevPos", i + 1),
                    teamName = r.optString("team"),
                    abbr = r.optString("abbr"),
                    points = r.optDouble("pts", 0.0),
                    crestUrl = r.optString("crestUrl"),
                    flagUrl = r.optString("flagUrl"),
                    isWomen = isWomen
                )
            )
        }
        return out
    }

    fun parseEspnScoreboard(
        jsonStr: String,
        leagueIdHint: String = "",
        leagueNameHint: String = ""
    ): List<RugbyMatch> {
        val root = JSONObject(jsonStr)
        val out = mutableListOf<RugbyMatch>()
        val events = root.optJSONArray("events") ?: JSONArray()
        for (i in 0 until events.length()) {
            val ev = events.optJSONObject(i) ?: continue
            val comp = ev.optJSONArray("competitions")?.optJSONObject(0) ?: continue
            val competitors = comp.optJSONArray("competitors") ?: continue
            if (competitors.length() < 2) continue

            var homeObj = competitors.optJSONObject(0) ?: JSONObject()
            var awayObj = competitors.optJSONObject(1) ?: JSONObject()
            for (c in 0 until competitors.length()) {
                val cand = competitors.optJSONObject(c) ?: continue
                if (cand.optString("homeAway") == "home") homeObj = cand
                if (cand.optString("homeAway") == "away") awayObj = cand
            }
            val homeTeam = homeObj.optJSONObject("team") ?: JSONObject()
            val awayTeam = awayObj.optJSONObject("team") ?: JSONObject()

            val statusObj = comp.optJSONObject("status") ?: ev.optJSONObject("status") ?: JSONObject()
            val typeObj = statusObj.optJSONObject("type") ?: JSONObject()
            val state = typeObj.optString("state", "pre").lowercase()
            val typeName = typeObj.optString("name", "").lowercase()
            val shortDetail = typeObj.optString("shortDetail", "")
            val status = when {
                "postpon" in typeName || "delay" in typeName -> MatchStatus.POSTPONED
                "cancel" in typeName -> MatchStatus.CANCELLED
                state == "in" -> MatchStatus.LIVE
                state == "post" -> MatchStatus.FINISHED
                else -> MatchStatus.SCHEDULED
            }
            val clock = statusObj.optString("displayClock", "")
            val periodNum = statusObj.optInt("period", 0)
            val isEt = periodNum > 2 || shortDetail.lowercase().let { "et" in it || "extra" in it || "ot" in it }
            val periodLabel = when {
                status == MatchStatus.LIVE && isEt -> "Tiempo extra"
                status == MatchStatus.LIVE && periodNum == 1 -> "1er Tiempo"
                status == MatchStatus.LIVE && periodNum == 2 -> "2do Tiempo"
                status == MatchStatus.FINISHED -> "Final"
                status == MatchStatus.POSTPONED -> "Postergado"
                status == MatchStatus.CANCELLED -> "Cancelado"
                else -> shortDetail.ifBlank { status.label }
            }
            val isoDate = ev.optString("date", "")
            val zdt = runCatching { Instant.parse(isoDate).atZone(artZone) }.getOrNull()
            val ts = zdt?.toEpochSecond() ?: 0L
            val hScore = homeObj.optString("score", "").toIntOrNull()
            val aScore = awayObj.optString("score", "").toIntOrNull()
            val leagueName = root.optJSONArray("leagues")?.optJSONObject(0)?.optString("name", leagueNameHint.ifBlank { "Rugby Union" })
                ?: leagueNameHint.ifBlank { "Rugby Union" }
            val leagueShort = root.optJSONArray("leagues")?.optJSONObject(0)?.optString("abbreviation", leagueName) ?: leagueName
            val hName = homeTeam.optString("displayName", homeTeam.optString("name"))
            val aName = awayTeam.optString("displayName", awayTeam.optString("name"))
            val venue = comp.optJSONObject("venue")
            val isArg = hName.contains("Argentina", true) || aName.contains("Argentina", true)

            out.add(
                RugbyMatch(
                    id = "espn_${ev.optString("id")}",
                    source = "sec_live",
                    leagueId = leagueIdHint.ifBlank {
                        root.optJSONArray("leagues")?.optJSONObject(0)?.optString("id", "rugby") ?: "rugby"
                    },
                    leagueName = leagueName,
                    leagueShort = leagueShort,
                    phase = "",
                    group = if (isArg) "nations" else "clubs",
                    category = if (isArg) "Selecciones" else "Clubes",
                    status = status,
                    minuteDisplay = clock,
                    periodDisplay = periodLabel,
                    dateArg = zdt?.let { argFmt.format(it) } ?: isoDate,
                    dayArg = zdt?.let { dayFmt.format(it) } ?: "",
                    timeArg = zdt?.let { timeFmt.format(it) } ?: "",
                    timestampSec = ts,
                    venueName = venue?.optString("fullName", "") ?: "",
                    venueCity = venue?.optJSONObject("address")?.optString("city", "") ?: "",
                    homeName = hName,
                    homeAbbr = homeTeam.optString("abbreviation", hName.take(3).uppercase()),
                    homeCrestUrl = homeTeam.optString("logo", ""),
                    homeFlagUrl = "",
                    homeCoach = "",
                    homeRank = null,
                    homeScore = if (status == MatchStatus.SCHEDULED) null else hScore,
                    awayName = aName,
                    awayAbbr = awayTeam.optString("abbreviation", aName.take(3).uppercase()),
                    awayCrestUrl = awayTeam.optString("logo", ""),
                    awayFlagUrl = "",
                    awayCoach = "",
                    awayRank = null,
                    awayScore = if (status == MatchStatus.SCHEDULED) null else aScore,
                    isArgentina = isArg,
                    verificationStatus = VerificationStatus.PENDING_VERIFICATION,
                    verifiedSources = listOf("sec_live"),
                    events = emptyList(),
                    liveStats = null,
                    statusNote = shortDetail.takeIf { it.isNotBlank() && (status == MatchStatus.POSTPONED || status == MatchStatus.CANCELLED) },
                    isExtraTime = isEt
                )
            )
        }
        return out
    }

    fun parseWorldRugbyMatches(jsonStr: String): List<RugbyMatch> = parseWorldRugbyLive(jsonStr)

    fun parseWorldRugbyLive(jsonStr: String): List<RugbyMatch> {
        val root = JSONObject(jsonStr)
        val content = root.optJSONArray("content") ?: return emptyList()
        val out = mutableListOf<RugbyMatch>()
        for (i in 0 until content.length()) {
            val m = content.optJSONObject(i) ?: continue
            val teams = m.optJSONArray("teams") ?: continue
            val scores = m.optJSONArray("scores")
            if (teams.length() < 2) continue
            val home = teams.optJSONObject(0) ?: continue
            val away = teams.optJSONObject(1) ?: continue
            val stRaw = m.optString("status", "U").uppercase()
            val status = when (stRaw) {
                "L" -> MatchStatus.LIVE
                "C" -> MatchStatus.FINISHED
                "P", "PPD" -> MatchStatus.POSTPONED
                "X", "CAN" -> MatchStatus.CANCELLED
                else -> MatchStatus.SCHEDULED
            }
            val timeObj = m.optJSONObject("time")
            val millis = timeObj?.optLong("millis", 0L) ?: 0L
            val zdt = if (millis > 0L) Instant.ofEpochMilli(millis).atZone(artZone) else null
            val hScore = if (status == MatchStatus.SCHEDULED || scores == null || scores.length() < 2) null else scores.optInt(0)
            val aScore = if (status == MatchStatus.SCHEDULED || scores == null || scores.length() < 2) null else scores.optInt(1)
            val clockSecs = m.optJSONObject("clock")?.optInt("secs", -1) ?: -1
            val clockDisp = if (clockSecs >= 0) "${clockSecs / 60}'" else ""
            val hName = home.optString("name")
            val aName = away.optString("name")
            val isArg = hName.contains("Argentina", true) || aName.contains("Argentina", true)
            val venue = m.optJSONObject("venue")
            val compName = m.optJSONArray("events")?.optJSONObject(0)?.optString("label", "Rugby Internacional") ?: "Rugby Internacional"

            out.add(
                RugbyMatch(
                    id = "wr_${m.optString("matchId")}",
                    source = "official_live",
                    leagueId = "wr",
                    leagueName = compName,
                    leagueShort = compName.take(18),
                    phase = "",
                    group = "nations",
                    category = "Selecciones",
                    status = status,
                    minuteDisplay = clockDisp,
                    periodDisplay = if (status == MatchStatus.FINISHED) "Final" else status.label,
                    dateArg = zdt?.let { argFmt.format(it) } ?: "",
                    dayArg = zdt?.let { dayFmt.format(it) } ?: "",
                    timeArg = zdt?.let { timeFmt.format(it) } ?: "",
                    timestampSec = millis / 1000L,
                    venueName = venue?.optString("name", "") ?: "",
                    venueCity = venue?.optString("city", "") ?: "",
                    homeName = hName,
                    homeAbbr = home.optString("abbreviation", hName.take(3).uppercase()),
                    homeCrestUrl = "",
                    homeFlagUrl = "",
                    homeCoach = "",
                    homeRank = null,
                    homeScore = hScore,
                    awayName = aName,
                    awayAbbr = away.optString("abbreviation", aName.take(3).uppercase()),
                    awayCrestUrl = "",
                    awayFlagUrl = "",
                    awayCoach = "",
                    awayRank = null,
                    awayScore = aScore,
                    isArgentina = isArg,
                    verificationStatus = VerificationStatus.PENDING_VERIFICATION,
                    verifiedSources = listOf("official_live"),
                    events = emptyList(),
                    liveStats = null
                )
            )
        }
        return out
    }

    fun parseEspnSummaryDetails(
        jsonStr: String,
        homeNameHint: String = "",
        awayNameHint: String = ""
    ): Pair<List<MatchEvent>, LiveMatchStats?> {
        val root = JSONObject(jsonStr)
        val events = mutableListOf<MatchEvent>()
        val headerComps = root.optJSONObject("header")?.optJSONArray("competitions")?.optJSONObject(0)?.optJSONArray("competitors")
        var homeId = ""
        if (headerComps != null) {
            for (i in 0 until headerComps.length()) {
                val c = headerComps.optJSONObject(i) ?: continue
                if (c.optString("homeAway") == "home") homeId = c.optString("id")
            }
        }

        val scoringPlays = root.optJSONArray("scoringPlays") ?: JSONArray()
        for (i in 0 until scoringPlays.length()) {
            val sp = scoringPlays.optJSONObject(i) ?: continue
            val text = sp.optString("text", "")
            val typeText = sp.optJSONObject("type")?.optString("text", "").orEmpty().lowercase()
            val evType = when {
                "try" in typeText -> MatchEventType.TRY
                "conv" in typeText -> MatchEventType.CONVERSION
                "pen" in typeText -> MatchEventType.PENALTY
                "drop" in typeText -> MatchEventType.DROP_GOAL
                "yellow" in typeText -> MatchEventType.YELLOW_CARD
                "red" in typeText -> MatchEventType.RED_CARD
                "sub" in typeText -> MatchEventType.SUBSTITUTION
                else -> MatchEventType.TRY
            }
            val min = sp.optJSONObject("clock")?.optString("displayValue", "")
                ?.replace("'", "")?.substringBefore(":")?.toIntOrNull() ?: 0
            val teamObj = sp.optJSONObject("team")
            val teamId = teamObj?.optString("id", "").orEmpty()
            val teamName = teamObj?.optString("displayName", "").orEmpty()
            val isHome = (homeId.isNotBlank() && teamId == homeId) ||
                (homeNameHint.isNotBlank() && teamName.equals(homeNameHint, true)) ||
                (awayNameHint.isNotBlank() && !teamName.equals(awayNameHint, true) && teamName.isBlank())

            events.add(
                MatchEvent(
                    id = "sp_${sp.optString("id", i.toString())}",
                    minute = min,
                    period = sp.optJSONObject("period")?.optString("number", "")?.let { "${it}T" } ?: "",
                    type = evType,
                    teamSide = if (isHome) "home" else "away",
                    player = text.substringBefore("-").trim(),
                    detail = text
                )
            )
        }

        var stats: LiveMatchStats? = null
        val teamsArr = root.optJSONObject("boxscore")?.optJSONArray("teams")
        if (teamsArr != null && teamsArr.length() >= 2) {
            fun statMap(idx: Int): Map<String, Int?> {
                val arr = teamsArr.optJSONObject(idx)?.optJSONArray("statistics") ?: return emptyMap()
                val m = mutableMapOf<String, Int?>()
                for (j in 0 until arr.length()) {
                    val item = arr.optJSONObject(j) ?: continue
                    val key = item.optString("name").lowercase()
                    val num = item.optString("displayValue").replace("%", "").trim().toIntOrNull()
                    m[key] = num
                }
                return m
            }
            val hStats = statMap(0)
            val aStats = statMap(1)
            val candidate = LiveMatchStats(
                possessionHomePct = hStats["possessionpct"] ?: hStats["possession"],
                possessionAwayPct = aStats["possessionpct"] ?: aStats["possession"],
                territoryHomePct = hStats["territorypct"] ?: hStats["territory"],
                territoryAwayPct = aStats["territorypct"] ?: aStats["territory"],
                tacklesHome = hStats["tackles"] ?: hStats["tacklesmade"],
                tacklesAway = aStats["tackles"] ?: aStats["tacklesmade"],
                lineoutsWonHome = hStats["lineoutswon"],
                lineoutsWonAway = aStats["lineoutswon"],
                scrumsWonHome = hStats["scrumswon"],
                scrumsWonAway = aStats["scrumswon"],
                penaltiesHome = hStats["penaltiesconceded"] ?: hStats["penalties"],
                penaltiesAway = aStats["penaltiesconceded"] ?: aStats["penalties"]
            )
            if (candidate.hasAnyMetric()) stats = candidate
        }
        return events.sortedBy { it.minute } to stats
    }

    fun parseEspnMatchDetail(jsonStr: String, homeIdHint: String = ""): Pair<List<MatchEvent>, LiveMatchStats?> =
        parseEspnSummaryDetails(jsonStr, homeIdHint, "")
}
