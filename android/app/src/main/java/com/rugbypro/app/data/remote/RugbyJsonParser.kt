package com.rugbypro.app.data.remote

import com.rugbypro.app.domain.model.CompetitionItem
import com.rugbypro.app.domain.model.LiveMatchStats
import com.rugbypro.app.domain.model.MatchEvent
import com.rugbypro.app.domain.model.MatchEventType
import com.rugbypro.app.domain.model.MatchOfficial
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
import com.rugbypro.app.domain.usecase.RugbyDateUtils
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object RugbyJsonParser {

    private val artZone: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
    private val argFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm")
    private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private fun parseClockSeconds(clockLabel: String): Int? {
        val clean = clockLabel.trim()
        if (clean.isBlank()) return null
        if (":" in clean) {
            val parts = clean.split(":")
            if (parts.size == 2) {
                val m = parts[0].filter { it.isDigit() }.toIntOrNull() ?: return null
                val s = parts[1].filter { it.isDigit() }.toIntOrNull() ?: 0
                return (m * 60) + s.coerceIn(0, 59)
            }
        }
        val minsOnly = clean.replace("+", "").replace("'", "").trim().toIntOrNull()
        return minsOnly?.let { it * 60 }
    }

    fun formatClockMmSs(secs: Int): String {
        val s = secs.coerceAtLeast(0)
        return String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60)
    }

    fun parseEngineSnapshot(jsonStr: String, savedNewsIds: Set<String> = emptySet()): RugbyDashboard {
        val root = JSONObject(jsonStr)
        val generatedAtArg = RugbyDateUtils.normalizeDisplayDate(root.optString("generatedAtArg", ""))

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
                    publishedLabel = RugbyDateUtils.normalizeDisplayDate(n.optString("dateArg", n.optString("publishedArg", n.optString("published", "")))),
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
        fun enrichLineup(lineup: List<PlayerProfile>, team: TeamProfile?): List<PlayerProfile> {
            val base = if (lineup.isNotEmpty()) lineup else team?.roster.orEmpty()
            if (base.isEmpty() || team == null || team.roster.isEmpty()) return base
            val rosterByName = team.roster.associateBy { it.name.trim().lowercase() }
            return base.map { p ->
                val ref = rosterByName[p.name.trim().lowercase()]
                if (ref != null) {
                    p.copy(
                        photoUrl = p.photoUrl ?: ref.photoUrl,
                        nationality = p.nationality ?: ref.nationality,
                        nationalityFlagUrl = p.nationalityFlagUrl ?: ref.nationalityFlagUrl,
                        age = p.age ?: ref.age,
                        height = p.height ?: ref.height,
                        weight = p.weight ?: ref.weight,
                        caps = p.caps ?: ref.caps
                    )
                } else p
            }
        }
        return matches.map { m ->
            val hTeam = byNorm[CrossVerifyMatchesUseCase.normalizeTeam(m.homeName)]
            val aTeam = byNorm[CrossVerifyMatchesUseCase.normalizeTeam(m.awayName)]
            val resolved = ResolveMatchKitsUseCase.resolveForMatch(hTeam, aTeam)
            m.copy(
                homeCrestUrl = m.homeCrestUrl.ifBlank { hTeam?.crestUrl.orEmpty() },
                homeFlagUrl = m.homeFlagUrl.ifBlank { hTeam?.flagUrl.orEmpty() }.ifBlank { countryFlagUrl(m.homeName) },
                homeCoach = m.homeCoach.ifBlank { hTeam?.coach.orEmpty() },
                awayCrestUrl = m.awayCrestUrl.ifBlank { aTeam?.crestUrl.orEmpty() },
                awayFlagUrl = m.awayFlagUrl.ifBlank { aTeam?.flagUrl.orEmpty() }.ifBlank { countryFlagUrl(m.awayName) },
                awayCoach = m.awayCoach.ifBlank { aTeam?.coach.orEmpty() },
                homeKit = resolved.homeKit,
                awayKit = resolved.awayKit,
                homeActiveKitRole = resolved.homeRole,
                awayActiveKitRole = resolved.awayRole,
                homeLineup = enrichLineup(m.homeLineup, hTeam),
                awayLineup = enrichLineup(m.awayLineup, aTeam)
            )
        }
    }

    private fun parseLineupArray(arr: JSONArray?): List<PlayerProfile> {
        if (arr == null) return emptyList()
        val list = mutableListOf<PlayerProfile>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val name = p.optString("name", "").trim()
            if (name.isBlank()) continue
            val rawHeight = if (p.isNull("height")) null else p.optString("height").trim().takeIf { it.isNotBlank() && it != "null" }
            val rawWeight = if (p.isNull("weight")) null else p.optString("weight").trim().takeIf { it.isNotBlank() && it != "null" }
            list.add(
                PlayerProfile(
                    jersey = p.optString("jersey", "-"),
                    name = name,
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
        return list
    }

    private fun translateOfficialRole(role: String): String {
        val r = role.trim().lowercase()
        return when {
            r == "referee" || ("referee" in r && "assistant" !in r && "video" !in r && "tmo" !in r) -> "Árbitro principal"
            "assistant referee 1" in r -> "Árbitro asistente 1"
            "assistant referee 2" in r -> "Árbitro asistente 2"
            "assistant" in r || "touch" in r -> "Árbitro asistente"
            "tmo" in r || "television" in r || "tv match" in r || "video" in r -> "TMO (Videoarbitraje)"
            "fpro" in r || "foul play" in r -> "Oficial de revisión (FPRO)"
            else -> role.ifBlank { "Oficial de partido" }
        }
    }

    private fun parseMatchOfficials(arr: JSONArray?): Pair<String, List<MatchOfficial>> {
        if (arr == null) return "" to emptyList()
        val list = mutableListOf<MatchOfficial>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val inner = obj.optJSONObject("official")
            val name = inner?.optJSONObject("name")?.optString("display")
                ?: obj.optString("name", obj.optString("displayName", "")).trim()
            if (name.isBlank()) continue
            val roleRaw = obj.optString("role", obj.optString("position", "")).trim()
            val country = inner?.optString("country", obj.optString("country", ""))?.trim().orEmpty()
            list.add(
                MatchOfficial(
                    name = name,
                    role = translateOfficialRole(roleRaw),
                    country = country
                )
            )
        }
        val mainRef = list.firstOrNull { it.role == "Árbitro principal" } ?: list.firstOrNull()
        val refLabel = mainRef?.let {
            if (it.country.isNotBlank()) "${it.name} (${it.country})" else it.name
        }.orEmpty()
        return refLabel to list
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
        val (refName, officialsList) = parseMatchOfficials(m.optJSONArray("officials"))
        val hLineup = parseLineupArray(home.optJSONArray("lineup") ?: home.optJSONArray("probableLineup"))
        val aLineup = parseLineupArray(away.optJSONArray("lineup") ?: away.optJSONArray("probableLineup"))

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
            dateArg = RugbyDateUtils.normalizeDisplayDate(m.optString("dateArg", "")),
            dayArg = RugbyDateUtils.normalizeDisplayDate(m.optString("dayArg", "")).substringBefore(" ·"),
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
            isExtraTime = m.optBoolean("extraTime", false) || rawNote.lowercase().let { "extra" in it || "prorroga" in it || "prórroga" in it },
            refereeName = refName,
            officials = officialsList,
            homeLineup = hLineup,
            awayLineup = aLineup
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
            val clock = statusObj.optString("displayClock", "").trim()
            val periodNum = statusObj.optInt("period", 0)
            val sdLow = shortDetail.lowercase()
            val isHt = status == MatchStatus.LIVE && ("ht" in sdLow || "half" in sdLow || "entretiempo" in sdLow || "descanso" in sdLow)
            val isEt = periodNum > 2 || sdLow.let { "et" in it || "extra" in it || "ot" in it }
            val periodLabel = when {
                isHt -> "Entretiempo"
                status == MatchStatus.LIVE && isEt -> "Tiempo extra"
                status == MatchStatus.LIVE && periodNum == 1 -> "1er Tiempo"
                status == MatchStatus.LIVE && periodNum == 2 -> "2do Tiempo"
                status == MatchStatus.FINISHED -> "Final"
                status == MatchStatus.POSTPONED -> "Postergado"
                status == MatchStatus.CANCELLED -> "Cancelado"
                else -> shortDetail.ifBlank { status.label }
            }
            val parsedEspnSecs = if (status == MatchStatus.LIVE) parseClockSeconds(clock) else null
            val nowSyncMs = System.currentTimeMillis()
            val formattedEspnClock = when {
                status != MatchStatus.LIVE -> ""
                isHt -> "Entretiempo"
                parsedEspnSecs != null && parsedEspnSecs > 0 -> formatClockMmSs(parsedEspnSecs)
                else -> clock
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
                    minuteDisplay = formattedEspnClock,
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
                    isExtraTime = isEt,
                    clockSeconds = parsedEspnSecs,
                    clockSyncedAtMs = if (parsedEspnSecs != null) nowSyncMs else 0L,
                    clockRunning = status == MatchStatus.LIVE && !isHt && parsedEspnSecs != null
                )
            )
        }
        return out
    }

    private fun countryFlagUrl(teamName: String): String {
        val low = teamName.lowercase().replace(" 7s", "").trim()
        val iso = when {
            "argentina" in low || "pumas" in low -> "ar"
            "samoa" in low -> "ws"
            "mexico" in low || "méxico" in low -> "mx"
            "colombia" in low -> "co"
            "poland" in low || "polonia" in low -> "pl"
            "chile" in low -> "cl"
            "uruguay" in low -> "uy"
            "brasil" in low || "brazil" in low -> "br"
            "paraguay" in low -> "py"
            "new zealand" in low || "all blacks" in low -> "nz"
            "south africa" in low || "springboks" in low -> "za"
            "australia" in low || "wallabies" in low -> "au"
            "fiji" in low -> "fj"
            "tonga" in low -> "to"
            "france" in low || "francia" in low -> "fr"
            "england" in low || "inglaterra" in low -> "gb-eng"
            "ireland" in low || "irlanda" in low -> "ie"
            "scotland" in low || "escocia" in low -> "gb-sct"
            "wales" in low || "gales" in low -> "gb-wls"
            "italy" in low || "italia" in low -> "it"
            "spain" in low || "españa" in low -> "es"
            "portugal" in low -> "pt"
            "georgia" in low -> "ge"
            "japan" in low || "japón" in low -> "jp"
            "china" in low && "hong kong" !in low -> "cn"
            "hong kong" in low -> "hk"
            "usa" in low || "united states" in low -> "us"
            "canada" in low || "canadá" in low -> "ca"
            "kenya" in low -> "ke"
            "uganda" in low -> "ug"
            "zimbabwe" in low -> "zw"
            "germany" in low || "alemania" in low -> "de"
            "belgium" in low -> "be"
            "romania" in low -> "ro"
            "namibia" in low -> "na"
            else -> ""
        }
        return if (iso.isNotBlank()) "https://flagcdn.com/w80/$iso.png" else ""
    }

    private fun mapWorldRugbyStatus(stRaw: String, millis: Long): Pair<MatchStatus, String> {
        val code = stRaw.trim().uppercase()
        val nowMs = System.currentTimeMillis()
        var status = when {
            code in setOf("C", "LFT", "FT") -> MatchStatus.FINISHED
            code in setOf("P", "PPD") -> MatchStatus.POSTPONED
            code in setOf("X", "CAN") -> MatchStatus.CANCELLED
            code in setOf("L", "L1", "L2", "LHT", "LET", "L1ET", "L2ET", "LSO", "1H", "2H", "HT", "ET") ||
                (code.startsWith("L") && code != "LFT") -> MatchStatus.LIVE
            else -> MatchStatus.SCHEDULED
        }
        if (status == MatchStatus.LIVE && millis > 0L && (nowMs - millis) > 6 * 3600_000L) {
            status = MatchStatus.FINISHED
        }
        if (status == MatchStatus.FINISHED && millis > 0L && millis > (nowMs + 10 * 60_000L)) {
            status = MatchStatus.SCHEDULED
        }
        val periodLabel = when {
            status == MatchStatus.FINISHED -> "Final"
            status == MatchStatus.POSTPONED -> "Postergado"
            status == MatchStatus.CANCELLED -> "Cancelado"
            code in setOf("L1", "1H") -> "1er Tiempo"
            code in setOf("L2", "2H") -> "2do Tiempo"
            code in setOf("LHT", "HT") -> "Entretiempo"
            code in setOf("LET", "L1ET", "L2ET", "ET") -> "Tiempo extra"
            status == MatchStatus.LIVE -> "En vivo"
            else -> status.label
        }
        return status to periodLabel
    }

    fun parseEspnScorepanel(jsonStr: String): List<RugbyMatch> {
        val root = JSONObject(jsonStr)
        val scoresArr = root.optJSONArray("scores") ?: return emptyList()
        val out = mutableListOf<RugbyMatch>()
        for (i in 0 until scoresArr.length()) {
            val block = scoresArr.optJSONObject(i) ?: continue
            val lg = block.optJSONArray("leagues")?.optJSONObject(0)
            val lid = lg?.optString("id", "rugby") ?: "rugby"
            val lName = lg?.optString("name", "Rugby Internacional") ?: "Rugby Internacional"
            out.addAll(parseEspnScoreboard(block.toString(), lid, lName))
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
            val timeObj = m.optJSONObject("time")
            val millis = timeObj?.optLong("millis", 0L) ?: 0L
            val (status, periodLabel) = mapWorldRugbyStatus(stRaw, millis)
            val zdt = if (millis > 0L) Instant.ofEpochMilli(millis).atZone(artZone) else null
            val hScore = if (status == MatchStatus.SCHEDULED || scores == null || scores.length() < 2) null else scores.optInt(0)
            val aScore = if (status == MatchStatus.SCHEDULED || scores == null || scores.length() < 2) null else scores.optInt(1)
            val clockObj = m.optJSONObject("clock")
            val clockLabel = clockObj?.optString("label", "")?.trim().orEmpty()
            val clockSecsRaw = clockObj?.optInt("secs", -1) ?: -1
            val parsedWrSecs = when {
                status != MatchStatus.LIVE -> null
                clockSecsRaw >= 0 -> clockSecsRaw
                else -> parseClockSeconds(clockLabel)
            }
            val isWrHalftime = stRaw in setOf("LHT", "HT")
            val isWrRunning = status == MatchStatus.LIVE && !isWrHalftime && stRaw in setOf("L1", "L2", "LET", "L1ET", "L2ET")
            val nowWrSync = System.currentTimeMillis()
            val clockDisp = when {
                status != MatchStatus.LIVE -> ""
                isWrHalftime -> "Entretiempo"
                parsedWrSecs != null && parsedWrSecs > 0 -> formatClockMmSs(parsedWrSecs)
                clockLabel.isNotBlank() && clockLabel != "00:00" -> clockLabel
                else -> ""
            }
            val hName = home.optString("name")
            val aName = away.optString("name")
            val isArg = hName.contains("Argentina", true) || aName.contains("Argentina", true)
            val venue = m.optJSONObject("venue")
            val evObj = m.optJSONArray("events")?.optJSONObject(0)
            val compName = evObj?.optString("label", m.optString("competition", "Rugby Internacional"))
                ?.ifBlank { "Rugby Internacional" } ?: "Rugby Internacional"
            val sport = m.optString("sport", evObj?.optString("sport", "") ?: "").lowercase()
            val isSevens = sport in setOf("mrs", "wrs") ||
                compName.contains("sevens", true) ||
                compName.contains("svns", true) ||
                hName.contains("7s", true) ||
                aName.contains("7s", true)
            val group = when {
                isSevens -> "sevens"
                compName.contains("top 14", true) -> "top14"
                compName.contains("premiership", true) -> "premiership"
                compName.contains("united rugby", true) || compName.contains("urc", true) -> "urc"
                compName.contains("super rugby", true) -> "super_rugby"
                else -> "nations"
            }
            val shortComp = compName
                .replace("Men's Sevens Series 2027", "SVNS 7s M")
                .replace("Women's Sevens Series 2027", "SVNS 7s F")
                .take(20)
            val phase = m.optString("eventPhase", "").replace("Pool Pool", "Pool").trim()

            out.add(
                RugbyMatch(
                    id = "wr_${m.optString("matchId")}",
                    source = "official_live",
                    leagueId = sport.ifBlank { "wr" },
                    leagueName = compName,
                    leagueShort = shortComp,
                    phase = phase,
                    group = group,
                    category = if (group in setOf("sevens", "nations")) "Selecciones" else "Clubes",
                    status = status,
                    minuteDisplay = clockDisp,
                    periodDisplay = periodLabel,
                    dateArg = zdt?.let { argFmt.format(it) } ?: "",
                    dayArg = zdt?.let { dayFmt.format(it) } ?: "",
                    timeArg = zdt?.let { timeFmt.format(it) } ?: "",
                    timestampSec = millis / 1000L,
                    venueName = venue?.optString("name", "") ?: "",
                    venueCity = venue?.optString("city", "") ?: "",
                    homeName = hName,
                    homeAbbr = home.optString("abbreviation", hName.take(3).uppercase()),
                    homeCrestUrl = "",
                    homeFlagUrl = countryFlagUrl(hName),
                    homeCoach = "",
                    homeRank = null,
                    homeScore = hScore,
                    awayName = aName,
                    awayAbbr = away.optString("abbreviation", aName.take(3).uppercase()),
                    awayCrestUrl = "",
                    awayFlagUrl = countryFlagUrl(aName),
                    awayCoach = "",
                    awayRank = null,
                    awayScore = aScore,
                    isArgentina = isArg,
                    verificationStatus = VerificationStatus.PENDING_VERIFICATION,
                    verifiedSources = listOf("official_live"),
                    events = emptyList(),
                    liveStats = null,
                    isExtraTime = stRaw in setOf("LET", "L1ET", "L2ET", "ET"),
                    clockSeconds = parsedWrSecs,
                    clockSyncedAtMs = if (parsedWrSecs != null) nowWrSync else 0L,
                    clockRunning = isWrRunning
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

    data class MatchLiveDetail(
        val events: List<MatchEvent>,
        val liveStats: LiveMatchStats?,
        val refereeName: String,
        val officials: List<MatchOfficial>,
        val homeLineup: List<PlayerProfile>,
        val awayLineup: List<PlayerProfile>,
        val updatedStatus: MatchStatus? = null,
        val updatedPeriodDisplay: String? = null,
        val updatedMinuteDisplay: String? = null,
        val updatedHomeScore: Int? = null,
        val updatedAwayScore: Int? = null,
        val updatedClockSeconds: Int? = null,
        val updatedClockSyncedAtMs: Long = 0L,
        val updatedClockRunning: Boolean? = null
    )

    fun parseEspnSummaryFull(
        jsonStr: String,
        homeNameHint: String = "",
        awayNameHint: String = ""
    ): MatchLiveDetail {
        val (events, stats) = parseEspnSummaryDetails(jsonStr, homeNameHint, awayNameHint)
        val root = JSONObject(jsonStr)
        val gameInfo = root.optJSONObject("gameInfo")
        val (refName, officials) = parseMatchOfficials(gameInfo?.optJSONArray("officials"))
        val rostersArr = root.optJSONArray("rosters") ?: JSONArray()
        var homeLineup = emptyList<PlayerProfile>()
        var awayLineup = emptyList<PlayerProfile>()
        for (i in 0 until rostersArr.length()) {
            val rb = rostersArr.optJSONObject(i) ?: continue
            val side = rb.optString("homeAway", "").lowercase()
            val tName = rb.optJSONObject("team")?.optString("displayName", "").orEmpty()
            val roster = rb.optJSONArray("roster") ?: JSONArray()
            val parsed = mutableListOf<PlayerProfile>()
            for (j in 0 until roster.length()) {
                val ent = roster.optJSONObject(j) ?: continue
                val ath = ent.optJSONObject("athlete") ?: continue
                val pname = ath.optString("displayName", ath.optString("fullName", "")).trim()
                if (pname.isBlank()) continue
                val jersey = ent.optString("jersey", "-")
                val posObj = ent.optJSONObject("position")
                val posName = posObj?.optString("displayName", posObj.optString("name", "")) ?: ""
                val posAbbr = posObj?.optString("abbreviation", "") ?: ""
                val starter = ent.optBoolean("starter", (jersey.toIntOrNull() ?: 99) <= 15)
                val photo = ath.optJSONObject("headshot")?.optString("href", "")?.takeIf { it.isNotBlank() }
                parsed.add(
                    PlayerProfile(
                        jersey = jersey,
                        name = pname,
                        position = posName,
                        posCode = posAbbr,
                        starter = starter,
                        captain = ent.optBoolean("captain", false),
                        age = if (ath.isNull("age")) null else ath.optInt("age"),
                        height = ath.optString("displayHeight", "").takeIf { it.isNotBlank() },
                        weight = ath.optString("displayWeight", "").takeIf { it.isNotBlank() },
                        caps = null,
                        photoUrl = photo
                    )
                )
            }
            if (side == "home" || (homeNameHint.isNotBlank() && tName.equals(homeNameHint, true))) {
                homeLineup = parsed
            } else {
                awayLineup = parsed
            }
        }
        val headerComp = root.optJSONObject("header")
            ?.optJSONArray("competitions")
            ?.optJSONObject(0)
        var upStatus: MatchStatus? = null
        var upPeriod: String? = null
        var upMinute: String? = null
        var upHomeScore: Int? = null
        var upAwayScore: Int? = null
        var upClockSecs: Int? = null
        var upClockSyncMs = 0L
        var upClockRunning: Boolean? = null
        if (headerComp != null) {
            val comps = headerComp.optJSONArray("competitors")
            if (comps != null) {
                for (i in 0 until comps.length()) {
                    val c = comps.optJSONObject(i) ?: continue
                    val ha = c.optString("homeAway", "").lowercase()
                    val scVal = c.optString("score", "").toIntOrNull()
                    if (ha == "home" && scVal != null) upHomeScore = scVal
                    if (ha == "away" && scVal != null) upAwayScore = scVal
                }
            }
            val stObj = headerComp.optJSONObject("status")
            val tpObj = stObj?.optJSONObject("type")
            if (stObj != null && tpObj != null) {
                val stState = tpObj.optString("state", "").lowercase()
                val tpName = tpObj.optString("name", "").lowercase()
                val shortDet = tpObj.optString("shortDetail", "")
                upStatus = when {
                    "postpon" in tpName || "delay" in tpName -> MatchStatus.POSTPONED
                    "cancel" in tpName -> MatchStatus.CANCELLED
                    stState == "in" -> MatchStatus.LIVE
                    stState == "post" -> MatchStatus.FINISHED
                    stState == "pre" -> MatchStatus.SCHEDULED
                    else -> null
                }
                val dispClock = stObj.optString("displayClock", "").trim()
                val periodNum = stObj.optInt("period", 0)
                val sdLow = shortDet.lowercase()
                val isHt = upStatus == MatchStatus.LIVE && ("ht" in sdLow || "half" in sdLow || "entretiempo" in sdLow || "descanso" in sdLow)
                val isEt = periodNum > 2 || "et" in sdLow || "extra" in sdLow || "ot" in sdLow
                upPeriod = when {
                    isHt -> "Entretiempo"
                    upStatus == MatchStatus.LIVE && isEt -> "Tiempo extra"
                    upStatus == MatchStatus.LIVE && periodNum == 1 -> "1er Tiempo"
                    upStatus == MatchStatus.LIVE && periodNum == 2 -> "2do Tiempo"
                    upStatus == MatchStatus.FINISHED -> "Final"
                    upStatus == MatchStatus.POSTPONED -> "Postergado"
                    upStatus == MatchStatus.CANCELLED -> "Cancelado"
                    else -> shortDet.takeIf { it.isNotBlank() }
                }
                if (upStatus == MatchStatus.LIVE) {
                    val parsedSecs = parseClockSeconds(dispClock)
                    upClockSecs = parsedSecs
                    upClockSyncMs = System.currentTimeMillis()
                    upClockRunning = !isHt && (parsedSecs != null)
                    upMinute = when {
                        isHt -> "Entretiempo"
                        parsedSecs != null && parsedSecs > 0 -> formatClockMmSs(parsedSecs)
                        else -> dispClock.takeIf { it.isNotBlank() }
                    }
                } else if (upStatus != null) {
                    upClockRunning = false
                    upMinute = ""
                }
            }
        }
        return MatchLiveDetail(
            events = events,
            liveStats = stats,
            refereeName = refName,
            officials = officials,
            homeLineup = homeLineup,
            awayLineup = awayLineup,
            updatedStatus = upStatus,
            updatedPeriodDisplay = upPeriod,
            updatedMinuteDisplay = upMinute,
            updatedHomeScore = upHomeScore,
            updatedAwayScore = upAwayScore,
            updatedClockSeconds = upClockSecs,
            updatedClockSyncedAtMs = upClockSyncMs,
            updatedClockRunning = upClockRunning
        )
    }

    fun parseWorldRugbyMatchDetail(
        summaryJson: String,
        timelineJson: String? = null,
        statsJson: String? = null,
        isSevensHint: Boolean = false
    ): MatchLiveDetail {
        val root = JSONObject(summaryJson)
        val matchObj = root.optJSONObject("match")
        val sportStr = (matchObj?.optString("sport", "") ?: root.optString("sport", "")).lowercase()
        val compStr = (matchObj?.optString("competition", "") ?: "").lowercase()
        val isSevens = isSevensHint || sportStr in setOf("mrs", "wrs") || "sevens" in compStr || "svns" in compStr
        val maxStarters = if (isSevens) 7 else 15

        val (refName, officials) = parseMatchOfficials(root.optJSONArray("officials"))
        val teamsArr = root.optJSONArray("teams") ?: JSONArray()
        val lineups = arrayOf(emptyList<PlayerProfile>(), emptyList<PlayerProfile>())
        val events = mutableListOf<MatchEvent>()
        val playerById = mutableMapOf<String, String>()

        for (idx in 0 until minOf(2, teamsArr.length())) {
            val tb = teamsArr.optJSONObject(idx) ?: continue
            val tl = tb.optJSONObject("teamList")
            val capsArr = tl?.optJSONArray("captainIds") ?: JSONArray()
            val caps = (0 until capsArr.length()).map { capsArr.optString(it) }.toSet()
            val listArr = tl?.optJSONArray("list") ?: JSONArray()
            val parsed = mutableListOf<PlayerProfile>()
            for (j in 0 until listArr.length()) {
                val ent = listArr.optJSONObject(j) ?: continue
                val pl = ent.optJSONObject("player") ?: continue
                val pname = pl.optJSONObject("name")?.optString("display", "")?.trim().orEmpty()
                if (pname.isBlank()) continue
                val pid = pl.optString("id", "")
                val pAltId = pl.optString("altId", "")
                if (pid.isNotBlank()) playerById[pid] = pname
                if (pAltId.isNotBlank()) playerById[pAltId] = pname
                val num = ent.optString("number", "-")
                val numInt = num.toIntOrNull() ?: 99
                val isSub = pl.optBoolean("isReplacement", false) || numInt > maxStarters
                val country = if (pl.isNull("country")) null else pl.optString("country").takeIf { it.isNotBlank() }
                parsed.add(
                    PlayerProfile(
                        jersey = num,
                        name = pname,
                        position = ent.optString("positionLabel", ""),
                        posCode = ent.optString("position", ""),
                        starter = !isSub,
                        captain = pid in caps || pAltId in caps,
                        age = pl.optJSONObject("age")?.let { if (it.isNull("years")) null else it.optInt("years") },
                        height = if (pl.isNull("height")) null else pl.optString("height").takeIf { it.isNotBlank() && it != "null" && it != "0" },
                        weight = if (pl.isNull("weight")) null else pl.optString("weight").takeIf { it.isNotBlank() && it != "null" && it != "0" },
                        caps = if (pl.isNull("caps")) null else pl.optInt("caps"),
                        nationality = country,
                        nationalityFlagUrl = country?.let { countryFlagUrl(it) }?.takeIf { it.isNotBlank() }
                    )
                )
            }
            lineups[idx] = parsed.sortedWith(compareBy<PlayerProfile> { !it.starter }.thenBy { it.jersey.toIntOrNull() ?: 99 })

            val sc = tb.optJSONObject("scoring")
            if (sc != null) {
                val sideKey = if (idx == 0) "home" else "away"
                val types = listOf(
                    "Try" to MatchEventType.TRY,
                    "Conversion" to MatchEventType.CONVERSION,
                    "Penalty" to MatchEventType.PENALTY,
                    "DropGoal" to MatchEventType.DROP_GOAL
                )
                for ((k, evType) in types) {
                    val arr = sc.optJSONArray(k) ?: continue
                    for (eIdx in 0 until arr.length()) {
                        val item = arr.optJSONObject(eIdx) ?: continue
                        val pName = item.optJSONObject("player")?.optJSONObject("name")?.optString("display", "").orEmpty()
                        val minStr = item.optJSONObject("time")?.optString("label", "0") ?: "0"
                        val minInt = minStr.replace("'", "").substringBefore(":").trim().toIntOrNull() ?: 0
                        events.add(
                            MatchEvent(
                                id = "wr_${sideKey}_${k}_$eIdx",
                                minute = minInt,
                                period = if (minInt <= (if (isSevens) 7 else 40)) "1T" else "2T",
                                type = evType,
                                teamSide = sideKey,
                                player = pName,
                                detail = "${evType.label} · $pName"
                            )
                        )
                    }
                }
            }
        }

        // Timeline minuto a minuto oficial (esencial para 7s y partidos en vivo)
        if (!timelineJson.isNullOrBlank()) {
            runCatching {
                val tlRoot = JSONObject(timelineJson)
                val tlArr = tlRoot.optJSONArray("timeline") ?: JSONArray()
                val tlEvents = mutableListOf<MatchEvent>()
                for (k in 0 until tlArr.length()) {
                    val ev = tlArr.optJSONObject(k) ?: continue
                    val tCode = ev.optString("type", "")
                    val tLabel = ev.optString("typeLabel", "")
                    val combined = "$tCode $tLabel".lowercase()
                    if (tCode == "MS" || "change match status" in combined) continue
                    val evType = when {
                        "yellow" in combined || tCode == "YC" -> MatchEventType.YELLOW_CARD
                        "red" in combined || tCode == "RC" -> MatchEventType.RED_CARD
                        "sub" in combined || "replacement" in combined -> MatchEventType.SUBSTITUTION
                        "try" in combined || tCode == "T5" -> MatchEventType.TRY
                        "conv" in combined || tCode == "C2" -> MatchEventType.CONVERSION
                        "pen" in combined || tCode == "P3" -> MatchEventType.PENALTY
                        "drop" in combined || tCode.startsWith("DG") -> MatchEventType.DROP_GOAL
                        else -> continue
                    }
                    val tObj = ev.optJSONObject("time")
                    val secs = tObj?.optInt("secs", -1) ?: -1
                    val lblTime = tObj?.optString("label", "").orEmpty()
                    val minute = when {
                        secs >= 0 -> secs / 60
                        lblTime.contains(":") -> lblTime.substringBefore(":").toIntOrNull() ?: 0
                        else -> lblTime.replace("'", "").trim().toIntOrNull() ?: 0
                    }
                    val phaseRaw = ev.optString("phase", "")
                    val periodLbl = when {
                        phaseRaw == "L1" || phaseRaw == "1H" -> "1T"
                        phaseRaw == "L2" || phaseRaw == "2H" -> "2T"
                        "ET" in phaseRaw -> "TE"
                        else -> if (minute <= (if (isSevens) 7 else 40)) "1T" else "2T"
                    }
                    val teamIdx = ev.optInt("teamIndex", 0)
                    val sideKey = if (teamIdx == 1) "away" else "home"
                    val pid = ev.optString("playerId", "")
                    val pAlt = ev.optString("playerAltId", "")
                    val pName = playerById[pid] ?: playerById[pAlt] ?: ""
                    val exactClock = lblTime.takeIf { it.isNotBlank() } ?: "${minute}'"
                    val detailLabel = when {
                        "miss" in combined && evType == MatchEventType.CONVERSION -> "Conversión fallada ($exactClock)"
                        evType == MatchEventType.SUBSTITUTION && "off" in combined -> "Sale del campo ($exactClock)"
                        evType == MatchEventType.SUBSTITUTION && "on" in combined -> "Ingresa al campo ($exactClock)"
                        else -> "${evType.label} ($exactClock)"
                    }
                    tlEvents.add(
                        MatchEvent(
                            id = "wrtl_${k}_${tCode}_${minute}",
                            minute = minute,
                            period = periodLbl,
                            type = evType,
                            teamSide = sideKey,
                            player = pName,
                            detail = if (pName.isNotBlank()) "$detailLabel · $pName" else detailLabel
                        )
                    )
                }
                if (tlEvents.isNotEmpty()) {
                    events.clear()
                    events.addAll(tlEvents)
                }
            }
        }

        // Estadísticas en vivo oficiales (7s y 15s)
        var parsedStats: LiveMatchStats? = null
        if (!statsJson.isNullOrBlank()) {
            runCatching {
                val stRoot = JSONObject(statsJson)
                val ts = stRoot.optJSONArray("teamStats")
                if (ts != null && ts.length() >= 2) {
                    val s0 = ts.optJSONObject(0)?.optJSONObject("stats")
                    val s1 = ts.optJSONObject(1)?.optJSONObject("stats")
                    fun pct(obj: JSONObject?, key: String): Int? {
                        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
                        val v = obj.optDouble(key, Double.NaN)
                        if (v.isNaN()) return null
                        return if (v <= 1.0) kotlin.math.round(v * 100.0).toInt() else kotlin.math.round(v).toInt()
                    }
                    fun num(obj: JSONObject?, key: String): Int? {
                        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
                        val v = obj.optDouble(key, Double.NaN)
                        return if (v.isNaN()) null else kotlin.math.round(v).toInt()
                    }
                    val cand = LiveMatchStats(
                        possessionHomePct = pct(s0, "Possession"),
                        possessionAwayPct = pct(s1, "Possession"),
                        territoryHomePct = pct(s0, "Territory"),
                        territoryAwayPct = pct(s1, "Territory"),
                        tacklesHome = num(s0, "Tackles"),
                        tacklesAway = num(s1, "Tackles"),
                        lineoutsWonHome = num(s0, "LineoutsWon"),
                        lineoutsWonAway = num(s1, "LineoutsWon"),
                        scrumsWonHome = num(s0, "ScrumsWon"),
                        scrumsWonAway = num(s1, "ScrumsWon"),
                        penaltiesHome = num(s0, "PenaltiesConceded"),
                        penaltiesAway = num(s1, "PenaltiesConceded"),
                        triesHome = num(s0, "Tries"),
                        triesAway = num(s1, "Tries"),
                        conversionsHome = num(s0, "Conversions"),
                        conversionsAway = num(s1, "Conversions"),
                        metresHome = num(s0, "Metres") ?: num(s0, "CarriesMetres"),
                        metresAway = num(s1, "Metres") ?: num(s1, "CarriesMetres"),
                        cleanBreaksHome = num(s0, "CleanBreaks"),
                        cleanBreaksAway = num(s1, "CleanBreaks"),
                        defendersBeatenHome = num(s0, "DefendersBeaten"),
                        defendersBeatenAway = num(s1, "DefendersBeaten"),
                        turnoversWonHome = num(s0, "TurnoversWon") ?: num(s0, "TurnoverWon"),
                        turnoversWonAway = num(s1, "TurnoversWon") ?: num(s1, "TurnoverWon")
                    )
                    if (cand.hasAnyMetric()) parsedStats = cand
                }
            }
        }

        // Marcador y reloj frescos desde /summary.match
        var upStatus: MatchStatus? = null
        var upPeriod: String? = null
        var upMinute: String? = null
        var upHomeScore: Int? = null
        var upAwayScore: Int? = null
        var upClockSecs: Int? = null
        var upClockSyncMs = 0L
        var upClockRunning: Boolean? = null
        if (matchObj != null) {
            val stRaw = matchObj.optString("status", "U").uppercase()
            val millis = matchObj.optJSONObject("time")?.optLong("millis", 0L) ?: 0L
            val (st, pLbl) = mapWorldRugbyStatus(stRaw, millis)
            upStatus = st
            upPeriod = pLbl
            val clk = matchObj.optJSONObject("clock")
            val clkLbl = clk?.optString("label", "")?.trim().orEmpty()
            val clkSecs = clk?.optInt("secs", -1) ?: -1
            val isHt = stRaw in setOf("LHT", "HT")
            upClockRunning = st == MatchStatus.LIVE && !isHt && stRaw in setOf("L1", "L2", "LET", "L1ET", "L2ET")
            upClockSecs = when {
                st != MatchStatus.LIVE -> null
                clkSecs >= 0 -> clkSecs
                else -> parseClockSeconds(clkLbl)
            }
            if (upClockSecs != null) upClockSyncMs = System.currentTimeMillis()
            upMinute = when {
                st != MatchStatus.LIVE -> ""
                isHt -> "Entretiempo"
                upClockSecs != null && upClockSecs > 0 -> formatClockMmSs(upClockSecs)
                clkLbl.isNotBlank() && clkLbl != "00:00" -> clkLbl
                else -> ""
            }
            val scArr = matchObj.optJSONArray("scores")
            if (st != MatchStatus.SCHEDULED && scArr != null && scArr.length() >= 2) {
                upHomeScore = scArr.optInt(0)
                upAwayScore = scArr.optInt(1)
            }
        }

        return MatchLiveDetail(
            events = events.sortedBy { it.minute },
            liveStats = parsedStats,
            refereeName = refName,
            officials = officials,
            homeLineup = lineups[0],
            awayLineup = lineups[1],
            updatedStatus = upStatus,
            updatedPeriodDisplay = upPeriod,
            updatedMinuteDisplay = upMinute,
            updatedHomeScore = upHomeScore,
            updatedAwayScore = upAwayScore,
            updatedClockSeconds = upClockSecs,
            updatedClockSyncedAtMs = upClockSyncMs,
            updatedClockRunning = upClockRunning
        )
    }

    fun parseWorldRugbyContentNews(jsonStr: String, lang: String = "es"): List<NewsArticle> {
        val root = JSONObject(jsonStr)
        val arr = root.optJSONArray("content") ?: return emptyList()
        val out = mutableListOf<NewsArticle>()
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val title = c.optString("title", "").trim()
            if (title.isBlank()) continue
            val id = "wr_${lang}_${c.optString("id", title.hashCode().toString())}"
            val rawSummary = c.optString("description", c.optString("summary", c.optString("subtitle", title)))
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifBlank { title }
            val lm = c.optJSONObject("leadMedia")
            val img = c.optString("imageUrl", "")
                .ifBlank { lm?.optString("imageUrl", "") ?: "" }
                .ifBlank { lm?.optString("onDemandUrl", "") ?: "" }
            val url = c.optString("canonicalUrl", "")
                .replace("http://", "https://")
                .ifBlank { "https://www.world.rugby/news/${c.optString("id")}" }
            val pubMillis = c.optLong("publishFrom", 0L)
            val dateStr = c.optString("date", "")
            val zdt = when {
                pubMillis > 0L -> runCatching { Instant.ofEpochMilli(pubMillis).atZone(artZone) }.getOrNull()
                dateStr.isNotBlank() -> runCatching { Instant.parse(dateStr).atZone(artZone) }.getOrNull()
                else -> null
            }
            val tagsArr = c.optJSONArray("tags") ?: JSONArray()
            val tagsText = buildString {
                for (t in 0 until tagsArr.length()) {
                    append(tagsArr.optJSONObject(t)?.optString("label", "") ?: "").append(' ')
                }
            }.lowercase()
            val lowTitle = "$title $rawSummary $tagsText".lowercase()
            val category = when {
                "pumas" in lowTitle || "argentina" in lowTitle || "jaguares" in lowTitle -> "Los Pumas"
                "svns" in lowTitle || "sevens" in lowTitle || "7s" in lowTitle -> "Seven / SVNS"
                "six nations" in lowTitle || "nations championship" in lowTitle -> "Torneos"
                else -> "Internacional"
            }
            out.add(
                NewsArticle(
                    id = id,
                    title = title,
                    summary = rawSummary.take(260),
                    source = "World Rugby ${lang.uppercase()}",
                    url = url,
                    imageUrl = img,
                    category = category,
                    publishedLabel = zdt?.let { argFmt.format(it) } ?: RugbyDateUtils.normalizeDisplayDate(dateStr),
                    savedOffline = false
                )
            )
        }
        return out
    }

    fun parseRssNews(xmlStr: String, sourceName: String, defaultCategory: String = "Internacional"): List<NewsArticle> {
        val out = mutableListOf<NewsArticle>()
        val itemRegex = Regex("<item\\b[^>]*>(.*?)</item>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val imgRegex = Regex("""(?:url|src)=["'](https?://[^"']+\.(?:jpg|jpeg|png|webp)[^"']*)["']""", RegexOption.IGNORE_CASE)
        fun extractTag(block: String, tag: String): String {
            val r = Regex("<$tag[^>]*>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?<\\/$tag>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            return r.find(block)?.groupValues?.getOrNull(1)
                ?.replace(Regex("<[^>]+>"), " ")
                ?.replace("&amp;", "&")
                ?.replace("&quot;", "\"")
                ?.replace("&#39;", "'")
                ?.replace("&apos;", "'")
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                .orEmpty()
        }
        for ((idx, m) in itemRegex.findAll(xmlStr).withIndex()) {
            if (idx >= 18) break
            val block = m.groupValues[1]
            val title = extractTag(block, "title")
            if (title.isBlank()) continue
            val link = extractTag(block, "link")
            val desc = extractTag(block, "description").ifBlank { title }
            val pubDate = extractTag(block, "pubDate")
            val imgUrl = imgRegex.find(block)?.groupValues?.getOrNull(1).orEmpty()
            val zdt = runCatching {
                java.time.ZonedDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME).withZoneSameInstant(artZone)
            }.getOrNull()
            val low = "$title $desc".lowercase()
            val cat = when {
                "pumas" in low || "argentina" in low || "urba" in low -> "Los Pumas"
                "sevens" in low || "svns" in low || "7s" in low -> "Seven / SVNS"
                else -> defaultCategory
            }
            out.add(
                NewsArticle(
                    id = "rss_${sourceName.lowercase().replace(" ", "_")}_${title.hashCode()}",
                    title = title,
                    summary = desc.take(260),
                    source = sourceName,
                    url = link,
                    imageUrl = imgUrl,
                    category = cat,
                    publishedLabel = zdt?.let { argFmt.format(it) } ?: RugbyDateUtils.normalizeDisplayDate(pubDate),
                    savedOffline = false
                )
            )
        }
        return out
    }
}
