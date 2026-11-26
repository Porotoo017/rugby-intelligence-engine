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
import com.rugbypro.app.domain.model.TeamProfile
import com.rugbypro.app.domain.model.TournamentStandings
import com.rugbypro.app.domain.model.VerificationStatus
import com.rugbypro.app.domain.model.WorldRankingRow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object RugbyJsonParser {
    private val artZone = ZoneId.of("America/Argentina/Buenos_Aires")
    private val argFmt = DateTimeFormatter.ofPattern("EEE dd MMM · HH:mm 'ART'", Locale("es", "AR"))
    private val dayFmt = DateTimeFormatter.ofPattern("EEE dd MMM", Locale("es", "AR"))
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm 'ART'", Locale("es", "AR"))

    fun parseEngineSnapshot(jsonStr: String, savedNewsIds: Set<String> = emptySet()): RugbyDashboard {
        val root = JSONObject(jsonStr)
        val generatedAtArg = root.optString("generatedAtArg", "Base local")

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
                    season = c.optString("season")
                )
            )
        }

        val matches = mutableListOf<RugbyMatch>()
        val matchesArr = root.optJSONArray("matches") ?: JSONArray()
        for (i in 0 until matchesArr.length()) {
            val m = matchesArr.optJSONObject(i) ?: continue
            matches.add(parseSnapshotMatch(m))
        }

        val nextXV = root.optJSONObject("argentinaNextXV")?.let { parseSnapshotMatch(it) }
            ?: root.optJSONObject("argentinaNext")?.let { parseSnapshotMatch(it) }
        val next7s = root.optJSONObject("argentinaNext7s")?.let { parseSnapshotMatch(it) }

        val argHub = root.optJSONObject("argentinaHub")
        val coachesObj = argHub?.optJSONObject("coaches")
        val coachXV = coachesObj?.optString("xv", "Felipe Contepomi") ?: "Felipe Contepomi"
        val coach7s = coachesObj?.optString("sevens", "Santiago Gómez Cora") ?: "Santiago Gómez Cora"

        // Standings from URBA championships + SRA + ESPN leagues if available
        val standingsList = mutableListOf<TournamentStandings>()
        val urbaObj = root.optJSONObject("urba")
        val urbaChamps = urbaObj?.optJSONArray("championships") ?: JSONArray()
        for (i in 0 until urbaChamps.length()) {
            val ch = urbaChamps.optJSONObject(i) ?: continue
            val rows = parseStandingsRows(ch.optJSONArray("standings"))
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
        val sraRows = parseStandingsRows(sraObj?.optJSONArray("standings"))
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

        // Teams directory + URBA clubs merged cleanly
        val teamsMap = linkedMapOf<String, TeamProfile>()
        val dirArr = root.optJSONArray("teamsDirectory") ?: JSONArray()
        for (i in 0 until dirArr.length()) {
            val t = dirArr.optJSONObject(i) ?: continue
            val profile = parseTeamProfile(t)
            if (profile.name.isNotBlank()) {
                teamsMap[profile.key.ifBlank { profile.name.lowercase() }] = profile
            }
        }
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
                    roster = emptyList()
                )
            }
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
                    category = n.optString("category", "General"),
                    publishedLabel = n.optString("publishedArg", n.optString("published", "")),
                    savedOffline = id in savedNewsIds
                )
            )
        }

        return RugbyDashboard(
            generatedAtArg = generatedAtArg,
            nextArgentinaXV = nextXV,
            nextArgentina7s = next7s,
            coachXV = coachXV,
            coach7s = coach7s,
            matches = matches,
            competitions = comps,
            standings = standingsList,
            teams = teamsMap.values.toList(),
            rankingsMen = rankingsMen,
            rankingsWomen = rankingsWomen,
            news = newsList,
            sourcesHealth = emptyList(),
            isOfflineFallback = false
        )
    }

    private fun parseSnapshotMatch(m: JSONObject): RugbyMatch {
        val status = when (m.optString("status").lowercase()) {
            "live", "in" -> MatchStatus.LIVE
            "finished", "post" -> MatchStatus.FINISHED
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
                    minute = ev.optInt("minute", 0),
                    period = ev.optString("period", ""),
                    type = evType,
                    teamSide = ev.optString("side", "home"),
                    player = ev.optString("player", ""),
                    detail = ev.optString("label", evType.label)
                )
            )
        }
        val cardsArr = m.optJSONArray("cards") ?: JSONArray()
        for (i in 0 until cardsArr.length()) {
            val c = cardsArr.optJSONObject(i) ?: continue
            val isRed = c.optString("color").lowercase().contains("red") || c.optString("color").lowercase().contains("roj")
            events.add(
                MatchEvent(
                    id = "cd_$i",
                    minute = c.optInt("minute", 0),
                    period = "",
                    type = if (isRed) MatchEventType.RED_CARD else MatchEventType.YELLOW_CARD,
                    teamSide = c.optString("side", "home"),
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
            phase = m.optString("phase"),
            group = m.optString("group", "clubs"),
            category = m.optString("category"),
            status = status,
            minuteDisplay = m.optString("clock", ""),
            periodDisplay = m.optString("statusText", status.label),
            dateArg = m.optString("dateArg", ""),
            dayArg = m.optString("dayArg", ""),
            timeArg = m.optString("timeArg", ""),
            timestampSec = m.optLong("ts", 0L),
            venueName = venue.optString("name", ""),
            venueCity = venue.optString("city", ""),
            homeName = home.optString("name"),
            homeAbbr = home.optString("abbr"),
            homeCrestUrl = home.optString("crestUrl"),
            homeFlagUrl = home.optString("flagUrl"),
            homeCoach = home.optString("coach"),
            homeRank = hRank,
            homeScore = hScore,
            awayName = away.optString("name"),
            awayAbbr = away.optString("abbr"),
            awayCrestUrl = away.optString("crestUrl"),
            awayFlagUrl = away.optString("flagUrl"),
            awayCoach = away.optString("coach"),
            awayRank = aRank,
            awayScore = aScore,
            isArgentina = m.optBoolean("isArgentina", false),
            verificationStatus = VerificationStatus.PENDING_VERIFICATION,
            verifiedSources = listOf(src, "github_snapshot"),
            events = events.sortedBy { it.minute },
            liveStats = null
        )
    }

    private fun parseStandingsRows(arr: JSONArray?): List<StandingRow> {
        if (arr == null) return emptyList()
        val out = mutableListOf<StandingRow>()
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            val pf = r.optInt("pf", 0)
            val pc = r.optInt("pc", 0)
            out.add(
                StandingRow(
                    pos = r.optInt("pos", i + 1),
                    teamName = r.optString("name"),
                    crestUrl = r.optString("crestUrl"),
                    played = r.optInt("played", 0),
                    won = r.optInt("won", 0),
                    drawn = r.optInt("tied", r.optInt("drawn", 0)),
                    lost = r.optInt("lost", 0),
                    pf = pf,
                    pc = pc,
                    diff = r.optInt("diff", pf - pc),
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
            roster.add(
                PlayerProfile(
                    jersey = p.optString("jersey", ""),
                    name = p.optString("name", ""),
                    position = p.optString("position", ""),
                    posCode = p.optString("posCode", ""),
                    starter = p.optBoolean("starter", false),
                    captain = p.optBoolean("captain", false),
                    age = if (p.isNull("age")) null else p.optInt("age"),
                    height = if (p.isNull("height")) null else p.optString("height"),
                    weight = if (p.isNull("weight")) null else p.optString("weight"),
                    caps = if (p.isNull("caps")) null else p.optInt("caps")
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
            roster = roster
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

    fun parseEspnScoreboard(jsonStr: String): List<RugbyMatch> {
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
            val status = when (state) {
                "in" -> MatchStatus.LIVE
                "post" -> MatchStatus.FINISHED
                else -> MatchStatus.SCHEDULED
            }
            val clock = statusObj.optString("displayClock", "")
            val periodNum = statusObj.optInt("period", 0)
            val periodLabel = when {
                status == MatchStatus.LIVE && periodNum == 1 -> "1er Tiempo"
                status == MatchStatus.LIVE && periodNum >= 2 -> "2do Tiempo"
                else -> typeObj.optString("shortDetail", status.label)
            }
            val isoDate = ev.optString("date", "")
            val zdt = runCatching { Instant.parse(isoDate).atZone(artZone) }.getOrNull()
            val ts = zdt?.toEpochSecond() ?: 0L
            val hScore = homeObj.optString("score", "").toIntOrNull()
            val aScore = awayObj.optString("score", "").toIntOrNull()
            val leagueName = root.optJSONArray("leagues")?.optJSONObject(0)?.optString("name", "Rugby Union") ?: "Rugby Union"
            val leagueShort = root.optJSONArray("leagues")?.optJSONObject(0)?.optString("abbreviation", leagueName) ?: leagueName
            val hName = homeTeam.optString("displayName", homeTeam.optString("name"))
            val aName = awayTeam.optString("displayName", awayTeam.optString("name"))
            val venue = comp.optJSONObject("venue")
            val isArg = hName.contains("Argentina", true) || aName.contains("Argentina", true)

            out.add(
                RugbyMatch(
                    id = "espn:${ev.optString("id")}",
                    source = "espn_live",
                    leagueId = root.optJSONArray("leagues")?.optJSONObject(0)?.optString("id", "espn") ?: "espn",
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
                    verifiedSources = listOf("espn_live"),
                    events = emptyList(),
                    liveStats = null
                )
            )
        }
        return out
    }

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
            val compName = m.optJSONArray("events")?.optJSONObject(0)?.optString("label", "World Rugby") ?: "World Rugby"

            out.add(
                RugbyMatch(
                    id = "wr:${m.optString("matchId")}",
                    source = "world_rugby",
                    leagueId = "wr",
                    leagueName = compName,
                    leagueShort = compName.take(18),
                    phase = "",
                    group = "nations",
                    category = "Selecciones",
                    status = status,
                    minuteDisplay = clockDisp,
                    periodDisplay = status.label,
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
                    verifiedSources = listOf("world_rugby"),
                    events = emptyList(),
                    liveStats = null
                )
            )
        }
        return out
    }

    fun parseEspnMatchDetail(jsonStr: String, homeIdHint: String = ""): Pair<List<MatchEvent>, LiveMatchStats?> {
        val root = JSONObject(jsonStr)
        val events = mutableListOf<MatchEvent>()
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
                else -> MatchEventType.TRY
            }
            val min = sp.optJSONObject("clock")?.optString("displayValue", "")
                ?.replace("'", "")?.substringBefore(":")?.toIntOrNull() ?: 0
            val teamId = sp.optJSONObject("team")?.optString("id", "").orEmpty()
            events.add(
                MatchEvent(
                    id = "sp_${sp.optString("id", i.toString())}",
                    minute = min,
                    period = sp.optJSONObject("period")?.optString("number", "")?.let { "${it}T" } ?: "",
                    type = evType,
                    teamSide = if (teamId == homeIdHint || homeIdHint.isBlank()) "home" else "away",
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
}
