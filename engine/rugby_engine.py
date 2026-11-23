#!/usr/bin/env python3
"""
Rugby Intelligence Pro Engine v1.0.0
100% Real Data: ESPN Rugby API + World Rugby PulseLive API + Wikipedia Coach/Crest Enrichment + SQLite DB
"""

from __future__ import annotations

import concurrent.futures
import datetime as dt
import html
import json
import os
import re
import sqlite3
import time
import urllib.error
import urllib.parse
import urllib.request
from zoneinfo import ZoneInfo

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DATA_DIR = os.path.join(ROOT, "data")
os.makedirs(DATA_DIR, exist_ok=True)
DB_PATH = os.path.join(DATA_DIR, "rugby_master.db")
OUT_DATA_JSON = os.path.join(DATA_DIR, "rugby_engine_output.json")
OUT_ROOT_JSON = os.path.join(ROOT, "rugby_engine_output.json")
OUT_HISTORY_JSON = os.path.join(DATA_DIR, "rugby_teams_history.json")
OUT_REPORT_MD = os.path.join(ROOT, "REPORT_ACTUAL.md")

ARG_TZ = ZoneInfo("America/Argentina/Buenos_Aires")
UTC_TZ = dt.timezone.utc
USER_AGENT = "RugbyIntelligencePro/1.0 (+https://github.com/Porotoo017/rugby-intelligence-engine)"

ESPN_LEAGUES = [
    {"id": "289279", "name": "URBA Top 14", "group": "arg", "short": "URBA Top 14"},
    {"id": "2009", "name": "URBA Primera A", "group": "arg", "short": "URBA 1ª A"},
    {"id": "17567", "name": "Nations Championship", "group": "nations", "short": "Nations Champ"},
    {"id": "244293", "name": "The Rugby Championship", "group": "nations", "short": "Rugby Champ"},
    {"id": "180659", "name": "Six Nations", "group": "nations", "short": "6 Naciones"},
    {"id": "289234", "name": "International Test Matches", "group": "nations", "short": "Test Match"},
    {"id": "268565", "name": "British & Irish Lions Tour", "group": "nations", "short": "Lions Tour"},
    {"id": "164205", "name": "Rugby World Cup", "group": "worldcup", "short": "Mundial RWC"},
    {"id": "289237", "name": "Women's Rugby World Cup", "group": "worldcup", "short": "Mundial Fem"},
    {"id": "282", "name": "Olympic Men's 7s", "group": "sevens", "short": "Seven Olímpico"},
    {"id": "283", "name": "Olympic Women's 7s", "group": "sevens", "short": "Seven Fem"},
    {"id": "271937", "name": "European Champions Cup", "group": "clubs", "short": "Champions Cup"},
    {"id": "272073", "name": "European Challenge Cup", "group": "clubs", "short": "Challenge Cup"},
    {"id": "270559", "name": "French Top 14", "group": "clubs", "short": "Top 14"},
    {"id": "270557", "name": "United Rugby Championship", "group": "clubs", "short": "URC"},
    {"id": "267979", "name": "Gallagher Premiership", "group": "clubs", "short": "Premiership"},
    {"id": "242041", "name": "Super Rugby Pacific", "group": "clubs", "short": "Super Rugby"},
    {"id": "270563", "name": "NPC Mitre 10 Cup", "group": "clubs", "short": "NPC NZ"},
    {"id": "289262", "name": "Major League Rugby", "group": "clubs", "short": "MLR USA"},
]

WIKI_PAGE_MAP = {
    "argentina": "Argentina_national_rugby_union_team",
    "argentina 7s": "Argentina_national_rugby_sevens_team",
    "new zealand": "New_Zealand_national_rugby_union_team",
    "new zealand 7s": "New_Zealand_national_rugby_sevens_team",
    "south africa": "South_Africa_national_rugby_union_team",
    "south africa 7s": "South_Africa_national_rugby_sevens_team",
    "france": "France_national_rugby_union_team",
    "france 7s": "France_national_rugby_sevens_team",
    "england": "England_national_rugby_union_team",
    "great britain 7s": "Great_Britain_national_rugby_sevens_team",
    "ireland": "Ireland_national_rugby_union_team",
    "scotland": "Scotland_national_rugby_union_team",
    "wales": "Wales_national_rugby_union_team",
    "australia": "Australia_national_rugby_union_team",
    "australia 7s": "Australia_national_rugby_sevens_team",
    "italy": "Italy_national_rugby_union_team",
    "fiji": "Fiji_national_rugby_union_team",
    "fiji 7s": "Fiji_national_rugby_sevens_team",
    "japan": "Japan_national_rugby_union_team",
    "georgia": "Georgia_national_rugby_union_team",
    "samoa": "Samoa_national_rugby_union_team",
    "tonga": "Tonga_national_rugby_union_team",
    "uruguay": "Uruguay_national_rugby_union_team",
    "chile": "Chile_national_rugby_union_team",
    "spain": "Spain_national_rugby_union_team",
    "portugal": "Portugal_national_rugby_union_team",
    "usa": "United_States_national_rugby_union_team",
    "usa 7s": "United_States_national_rugby_sevens_team",
    "canada": "Canada_national_rugby_union_team",
    "romania": "Romania_national_rugby_union_team",
    "hong kong china": "Hong_Kong_national_rugby_union_team",
    "stade toulousain": "Stade_Toulousain",
    "toulouse": "Stade_Toulousain",
    "union bordeaux-begles": "Union_Bordeaux_Bègles",
    "bordeaux": "Union_Bordeaux_Bègles",
    "stade rochelais": "Stade_Rochelais",
    "la rochelle": "Stade_Rochelais",
    "racing 92": "Racing_92",
    "stade francais paris": "Stade_Français",
    "rc toulon": "RC_Toulon",
    "toulon": "RC_Toulon",
    "section paloise": "Section_Paloise",
    "aviron bayonnais": "Aviron_Bayonnais",
    "montpellier herault rugby club": "Montpellier_Hérault_Rugby",
    "castres olympique": "Castres_Olympique",
    "lyon olympique universitaire": "Lyon_OU",
    "asm clermont auvergne": "ASM_Clermont_Auvergne",
    "usa perpignan": "USA_Perpignan",
    "rc vannes": "RC_Vannes",
    "leinster": "Leinster_Rugby",
    "munster": "Munster_Rugby",
    "ulster": "Ulster_Rugby",
    "bath rugby": "Bath_Rugby",
    "saracens": "Saracens_F.C.",
    "harlequins": "Harlequin_F.C.",
    "bristol bears": "Bristol_Bears",
    "newman": "Club_Newman",
    "hindu": "Hindú_Club",
    "sic": "San_Isidro_Club",
    "casi": "Club_Atlético_San_Isidro",
    "belgrano ac": "Belgrano_Athletic_Club",
    "cuba": "Club_Universitario_de_Buenos_Aires",
    "alumni": "Asociación_Alumni",
    "regatas bella vista": "Club_de_Regatas_Bella_Vista",
    "b.a.c.r.c.": "Buenos_Aires_Cricket_%26_Rugby_Club",
    "san luis": "Club_San_Luis",
    "champagnat": "Club_Champagnat",
    "club atletico del rosario": "Club_Atlético_del_Rosario",
}

POS_ES = {
    "Prop": "Pilar",
    "Loosehead Prop": "Pilar Izq.",
    "Tighthead Prop": "Pilar Der.",
    "Hooker": "Hooker",
    "Lock": "Segunda Línea",
    "Second Row": "Segunda Línea",
    "Flanker": "Ala",
    "Blindside Flanker": "Ala Ciego",
    "Openside Flanker": "Ala Abierto",
    "Number 8": "Octavo",
    "No. 8": "Octavo",
    "Scrum-half": "Medio Scrum",
    "Scrumhalf": "Medio Scrum",
    "Fly-half": "Apertura",
    "Flyhalf": "Apertura",
    "Centre": "Centro",
    "Inside Centre": "1º Centro",
    "Outside Centre": "2º Centro",
    "Wing": "Wing",
    "Fullback": "Fullback",
    "Forward": "Forward",
    "Back": "Back",
    "Replacement": "Suplente",
    "Substitute": "Suplente",
}


def norm_key(text: str) -> str:
    s = (text or "").strip().lower()
    for a, b in [
        ("á", "a"), ("é", "e"), ("í", "i"), ("ó", "o"), ("ú", "u"), ("ü", "u"),
        ("ñ", "n"), ("è", "e"), ("ê", "e"), ("à", "a"), ("ç", "c"),
    ]:
        s = s.replace(a, b)
    s = re.sub(r"[^a-z0-9.\s&-]", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def clean_url(url: str | None) -> str:
    if not url:
        return ""
    u = url.strip()
    if u.startswith("http://"):
        u = "https://" + u[7:]
    if "wikimedia.org" in u or "wikipedia.org" in u:
        u = u.split("?")[0]
    return u


def fetch_json(url: str, timeout: int = 12) -> dict | list | None:
    try:
        req = urllib.request.Request(
            url,
            headers={"User-Agent": USER_AGENT, "Accept": "application/json"},
        )
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except Exception:
        return None


def parse_iso_dt(raw: str | int | float | None) -> dt.datetime | None:
    if raw is None or raw == "":
        return None
    if isinstance(raw, (int, float)):
        sec = raw / 1000.0 if raw > 1e11 else float(raw)
        return dt.datetime.fromtimestamp(sec, tz=UTC_TZ)
    s = str(raw).strip()
    try:
        if s.endswith("Z"):
            s = s[:-1] + "+00:00"
        d = dt.datetime.fromisoformat(s)
        if d.tzinfo is None:
            d = d.replace(tzinfo=UTC_TZ)
        return d.astimezone(UTC_TZ)
    except Exception:
        return None


def format_arg_times(utc_dt: dt.datetime | None) -> dict:
    if not utc_dt:
        return {"dateUtc": "", "dateArg": "Por confirmar", "dayArg": "", "timeArg": "", "ts": 0}
    arg_dt = utc_dt.astimezone(ARG_TZ)
    dias = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"]
    meses = ["Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"]
    day_str = f"{dias[arg_dt.weekday()]} {arg_dt.day:02d} {meses[arg_dt.month - 1]}"
    time_str = f"{arg_dt.hour:02d}:{arg_dt.minute:02d}"
    return {
        "dateUtc": utc_dt.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "dateArg": f"{day_str} · {time_str} ART",
        "dayArg": day_str,
        "timeArg": f"{time_str} ART",
        "ts": int(utc_dt.timestamp()),
    }


def init_db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS match_history (
            match_key TEXT PRIMARY KEY,
            source TEXT,
            league_id TEXT,
            league_name TEXT,
            group_key TEXT,
            date_utc TEXT,
            date_arg TEXT,
            home_team TEXT,
            away_team TEXT,
            home_score INTEGER,
            away_score INTEGER,
            venue TEXT,
            city TEXT,
            status TEXT,
            updated_at TEXT
        );
        CREATE TABLE IF NOT EXISTS team_meta (
            team_key TEXT PRIMARY KEY,
            display_name TEXT,
            espn_id TEXT,
            crest_url TEXT,
            coach TEXT,
            stadium TEXT,
            country TEXT,
            wiki_title TEXT,
            updated_at TEXT
        );
        CREATE TABLE IF NOT EXISTS team_rosters (
            team_key TEXT PRIMARY KEY,
            team_name TEXT,
            league_name TEXT,
            event_date TEXT,
            lineup_json TEXT,
            updated_at TEXT
        );
        """
    )
    conn.commit()
    return conn


def translate_pos(pos: str) -> str:
    if not pos:
        return "Jugador"
    p = pos.strip()
    return POS_ES.get(p, POS_ES.get(p.title(), p))


def parse_wiki_coach_and_crest(wiki_title: str) -> tuple[str | None, str | None, str | None]:
    coach = None
    crest = None
    stadium = None

    encoded = urllib.parse.quote(wiki_title, safe="%_")
    summary = fetch_json(f"https://en.wikipedia.org/api/rest_v1/page/summary/{encoded}", timeout=8)
    if isinstance(summary, dict):
        thumb = (summary.get("thumbnail") or {}).get("source") or (summary.get("originalimage") or {}).get("source")
        if thumb:
            crest = clean_url(thumb)

    time.sleep(0.14)
    sec0 = fetch_json(
        f"https://en.wikipedia.org/w/api.php?action=parse&page={encoded}&prop=text&section=0&format=json",
        timeout=8,
    )
    raw = ((sec0 or {}).get("parse", {}).get("text", {}) or {}).get("*", "") if isinstance(sec0, dict) else ""
    if raw:
        rows = re.findall(r"<tr[^>]*>(.*?)</tr>", raw, re.S | re.I)
        for r in rows:
            th = re.search(r"<th[^>]*>(.*?)</th>", r, re.S | re.I)
            td = re.search(r"<td[^>]*>(.*?)</td>", r, re.S | re.I)
            if not (th and td):
                continue
            k = html.unescape(re.sub(r"<[^>]+>", " ", th.group(1))).strip().lower()
            val = html.unescape(re.sub(r"\[[^\]]*\]", "", re.sub(r"<[^>]+>", " ", td.group(1))))
            val = re.sub(r"\s+", " ", val).strip()
            if not val:
                continue
            if not coach and any(x in k for x in ("coach", "head coach", "director of rugby", "entrenador")):
                coach = val[:90]
            if not stadium and any(x in k for x in ("ground", "stadium", "home ground", "estadio")):
                stadium = val[:100]
    return coach, crest, stadium


def harvest_espn_teams(conn: sqlite3.Connection) -> dict[str, dict]:
    team_index: dict[str, dict] = {}
    now_iso = dt.datetime.now(UTC_TZ).strftime("%Y-%m-%dT%H:%M:%SZ")

    def fetch_league_teams(lg):
        d = fetch_json(f"https://site.api.espn.com/apis/site/v2/sports/rugby/{lg['id']}/teams?limit=60", timeout=10)
        return lg, d

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as ex:
        for lg, d in ex.map(fetch_league_teams, ESPN_LEAGUES):
            if not isinstance(d, dict):
                continue
            sports = d.get("sports") or []
            if not sports:
                continue
            leagues = sports[0].get("leagues") or []
            if not leagues:
                continue
            for item in leagues[0].get("teams") or []:
                tm = item.get("team") or {}
                tid = str(tm.get("id") or "")
                name = str(tm.get("displayName") or tm.get("name") or "").strip()
                if not name:
                    continue
                logos = tm.get("logos") or []
                crest = clean_url(logos[0].get("href")) if logos else (f"https://a.espncdn.com/i/teamlogos/rugby/teams/500/{tid}.png" if tid else "")
                nk = norm_key(name)
                entry = {
                    "key": nk,
                    "name": name,
                    "abbr": tm.get("abbreviation") or "",
                    "espnId": tid,
                    "crestUrl": crest,
                    "leagueName": lg["name"],
                    "group": lg["group"],
                }
                if nk not in team_index or not team_index[nk].get("crestUrl"):
                    team_index[nk] = entry
                existing = conn.execute("SELECT coach, stadium, wiki_title FROM team_meta WHERE team_key=?", (nk,)).fetchone()
                conn.execute(
                    """INSERT INTO team_meta(team_key, display_name, espn_id, crest_url, coach, stadium, country, wiki_title, updated_at)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                       ON CONFLICT(team_key) DO UPDATE SET
                         display_name=excluded.display_name,
                         espn_id=CASE WHEN excluded.espn_id != '' THEN excluded.espn_id ELSE team_meta.espn_id END,
                         crest_url=CASE WHEN excluded.crest_url != '' THEN excluded.crest_url ELSE team_meta.crest_url END,
                         updated_at=excluded.updated_at""",
                    (
                        nk,
                        name,
                        tid,
                        crest,
                        existing[0] if existing else "",
                        existing[1] if existing else "",
                        "",
                        existing[2] if existing else "",
                        now_iso,
                    ),
                )
    conn.commit()
    return team_index


def enrich_team_meta_from_wiki(conn: sqlite3.Connection, team_names: list[str], team_index: dict[str, dict]) -> None:
    now = dt.datetime.now(UTC_TZ)
    cutoff = (now - dt.timedelta(days=7)).strftime("%Y-%m-%dT%H:%M:%SZ")
    lookups = 0
    seen_nk = set()

    for raw_name in team_names:
        nk = norm_key(raw_name)
        if not nk or nk in seen_nk or any(skip in nk for skip in ("winner", "loser", "pool ", "1st ", "2nd ", "3rd ", "4th ")):
            continue
        seen_nk.add(nk)
        base_nk = re.sub(r"\s+7s$", "", nk)
        wiki_title = WIKI_PAGE_MAP.get(nk) or WIKI_PAGE_MAP.get(base_nk)
        if not wiki_title:
            continue

        row = conn.execute(
            "SELECT crest_url, coach, stadium, updated_at FROM team_meta WHERE team_key=?",
            (nk,),
        ).fetchone()
        if row and row[1] and row[3] and row[3] >= cutoff:
            if nk in team_index:
                team_index[nk]["coach"] = row[1]
                if row[2]:
                    team_index[nk]["stadium"] = row[2]
                if not team_index[nk].get("crestUrl") and row[0]:
                    team_index[nk]["crestUrl"] = row[0]
            continue

        if lookups >= 30:
            if row:
                team_index.setdefault(nk, {"key": nk, "name": raw_name})
                if row[1]:
                    team_index[nk]["coach"] = row[1]
                if row[0] and not team_index[nk].get("crestUrl"):
                    team_index[nk]["crestUrl"] = row[0]
                if row[2]:
                    team_index[nk]["stadium"] = row[2]
            continue

        coach, w_crest, stadium = parse_wiki_coach_and_crest(wiki_title)
        lookups += 1
        now_iso = now.strftime("%Y-%m-%dT%H:%M:%SZ")
        existing_crest = (team_index.get(nk) or {}).get("crestUrl") or (team_index.get(base_nk) or {}).get("crestUrl") or (row[0] if row else "")
        final_crest = existing_crest or w_crest or ""

        conn.execute(
            """INSERT INTO team_meta(team_key, display_name, espn_id, crest_url, coach, stadium, country, wiki_title, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT(team_key) DO UPDATE SET
                 crest_url=CASE WHEN team_meta.crest_url='' THEN excluded.crest_url ELSE team_meta.crest_url END,
                 coach=CASE WHEN excluded.coach != '' THEN excluded.coach ELSE team_meta.coach END,
                 stadium=CASE WHEN excluded.stadium != '' THEN excluded.stadium ELSE team_meta.stadium END,
                 wiki_title=excluded.wiki_title,
                 updated_at=excluded.updated_at""",
            (
                nk,
                raw_name,
                (team_index.get(nk) or {}).get("espnId", ""),
                final_crest,
                coach or (row[1] if row else ""),
                stadium or (row[2] if row else ""),
                "",
                wiki_title,
                now_iso,
            ),
        )
        team_index.setdefault(nk, {"key": nk, "name": raw_name})
        if coach:
            team_index[nk]["coach"] = coach
        if final_crest:
            team_index[nk]["crestUrl"] = final_crest
        if stadium:
            team_index[nk]["stadium"] = stadium

    conn.commit()


def classify_group(league_name: str, sport: str = "") -> str:
    low = (league_name or "").lower()
    sp = (sport or "").lower()
    if sp in ("mrs", "wrs") or any(k in low for k in ("sevens", "svns", " 7s", "olympic")):
        return "sevens"
    if any(k in low for k in ("world cup", "mundial")):
        return "worldcup"
    if any(k in low for k in ("urba", "nacional de clubes", "super rugby americas")):
        return "arg"
    if any(k in low for k in ("nations", "six nations", "rugby championship", "international", "lions tour", "pacific nations", "bledisloe", "wxv", "rugby europe")):
        return "nations"
    return "clubs"


def parse_espn_matches(now_utc: dt.datetime) -> list[dict]:
    matches: dict[str, dict] = {}

    def fetch_league_sb(lg):
        urls = [f"https://site.api.espn.com/apis/site/v2/sports/rugby/{lg['id']}/scoreboard"]
        base_sb = fetch_json(urls[0], timeout=10)
        events = list((base_sb or {}).get("events") or []) if isinstance(base_sb, dict) else []

        cal = ((base_sb or {}).get("leagues") or [{}])[0].get("calendar") or [] if isinstance(base_sb, dict) else []
        date_queries = []
        for raw_d in cal:
            cdt = parse_iso_dt(raw_d)
            if cdt and (now_utc - dt.timedelta(days=10)) <= cdt <= (now_utc + dt.timedelta(days=35)):
                d_str = cdt.strftime("%Y%m%d")
                if d_str not in date_queries:
                    date_queries.append(d_str)
        for dq in date_queries[:6]:
            extra = fetch_json(f"{urls[0]}?dates={dq}", timeout=8)
            if isinstance(extra, dict):
                events.extend(extra.get("events") or [])
        return lg, events

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as ex:
        for lg, events in ex.map(fetch_league_sb, ESPN_LEAGUES):
            for ev in events:
                eid = str(ev.get("id") or "")
                if not eid:
                    continue
                comp = (ev.get("competitions") or [{}])[0]
                comps = comp.get("competitors") or []
                if len(comps) < 2:
                    continue
                home = next((c for c in comps if c.get("homeAway") == "home"), comps[0])
                away = next((c for c in comps if c.get("homeAway") == "away"), comps[1])
                h_tm = home.get("team") or {}
                a_tm = away.get("team") or {}
                h_name = str(h_tm.get("displayName") or h_tm.get("name") or "Local").strip()
                a_name = str(a_tm.get("displayName") or a_tm.get("name") or "Visitante").strip()

                state = str((ev.get("status") or {}).get("type", {}).get("state") or "pre").lower()
                completed = bool((ev.get("status") or {}).get("type", {}).get("completed"))
                status = "finished" if (state == "post" or completed) else ("live" if state == "in" else "scheduled")

                utc_dt = parse_iso_dt(ev.get("date"))
                if status == "scheduled" and utc_dt and utc_dt < (now_utc - dt.timedelta(hours=18)):
                    continue
                if status == "finished" and utc_dt and utc_dt > (now_utc + dt.timedelta(minutes=10)):
                    status = "scheduled"
                if status == "finished" and utc_dt and utc_dt < (now_utc - dt.timedelta(days=50)) and lg["id"] not in ("289279", "244293"):
                    continue

                times = format_arg_times(utc_dt)
                ven = comp.get("venue") or {}
                addr = ven.get("address") or {}
                odds_arr = comp.get("odds") or []
                odds_obj = None
                if odds_arr and isinstance(odds_arr[0], dict):
                    o = odds_arr[0]
                    odds_obj = {
                        "provider": (o.get("provider") or {}).get("name"),
                        "details": o.get("details"),
                        "overUnder": o.get("overUnder"),
                    }

                key = f"espn:{lg['id']}:{eid}"
                is_arg = ("argentina" in h_name.lower() or "argentina" in a_name.lower() or lg["group"] == "arg")
                matches[key] = {
                    "id": key,
                    "source": "espn",
                    "eventId": eid,
                    "leagueId": lg["id"],
                    "leagueName": lg["name"],
                    "leagueShort": lg["short"],
                    "group": lg["group"],
                    "status": status,
                    "statusText": str((ev.get("status") or {}).get("type", {}).get("shortDetail") or status),
                    "isArgentina": is_arg,
                    "venue": {
                        "name": ven.get("fullName") or "",
                        "city": addr.get("city") or "",
                        "country": addr.get("state") or addr.get("country") or "",
                    },
                    "odds": odds_obj,
                    **times,
                    "home": {
                        "name": h_name,
                        "abbr": h_tm.get("abbreviation") or "",
                        "espnId": str(h_tm.get("id") or ""),
                        "crestUrl": clean_url(h_tm.get("logo") or (f"https://a.espncdn.com/i/teamlogos/rugby/teams/500/{h_tm.get('id')}.png" if h_tm.get("id") else "")),
                        "score": home.get("score") if status != "scheduled" else None,
                        "lineup": [],
                        "coach": "",
                    },
                    "away": {
                        "name": a_name,
                        "abbr": a_tm.get("abbreviation") or "",
                        "espnId": str(a_tm.get("id") or ""),
                        "crestUrl": clean_url(a_tm.get("logo") or (f"https://a.espncdn.com/i/teamlogos/rugby/teams/500/{a_tm.get('id')}.png" if a_tm.get("id") else "")),
                        "score": away.get("score") if status != "scheduled" else None,
                        "lineup": [],
                        "coach": "",
                    },
                    "scoring": [],
                    "cards": [],
                    "officials": [],
                }
    return list(matches.values())


def parse_world_rugby_matches(now_utc: dt.datetime) -> list[dict]:
    start = (now_utc - dt.timedelta(days=12)).strftime("%Y-%m-%d")
    end = (now_utc + dt.timedelta(days=35)).strftime("%Y-%m-%d")
    out: list[dict] = []

    for page in range(0, 3):
        url = f"https://api.wr-rims-prod.pulselive.com/rugby/v3/match?startDate={start}&endDate={end}&pageSize=100&page={page}&sort=asc"
        d = fetch_json(url, timeout=12)
        items = (d or {}).get("content") or [] if isinstance(d, dict) else []
        if not items:
            break
        for m in items:
            mid = str(m.get("matchId") or "")
            tms = m.get("teams") or []
            if not mid or len(tms) < 2:
                continue
            h_name = str(tms[0].get("name") or "Local").strip()
            a_name = str(tms[1].get("name") or "Visitante").strip()
            ev_list = m.get("events") or []
            ev_label = str((ev_list[0].get("label") if ev_list else None) or m.get("competition") or "World Rugby").strip()
            sport = str(m.get("sport") or (ev_list[0].get("sport") if ev_list else "") or "").lower()
            group = classify_group(ev_label, sport)

            wr_status = str(m.get("status") or "U").upper()
            status = "finished" if wr_status == "C" else ("live" if wr_status in ("L", "1H", "2H", "HT", "ET") else "scheduled")

            t_obj = m.get("time") or {}
            utc_dt = parse_iso_dt(t_obj.get("millis")) or parse_iso_dt(t_obj.get("label"))
            if status == "finished" and utc_dt and utc_dt > (now_utc + dt.timedelta(minutes=10)):
                status = "scheduled"
            if status == "scheduled" and utc_dt and utc_dt < (now_utc - dt.timedelta(hours=18)):
                continue
            times = format_arg_times(utc_dt)

            scores = m.get("scores") or [0, 0]
            ven = m.get("venue") or {}
            clk = (m.get("clock") or {}).get("label") or ""
            status_text = clk if (status == "live" and clk) else ("Finalizado" if status == "finished" else "Programado")
            is_arg = ("argentina" in h_name.lower() or "argentina" in a_name.lower())

            out.append({
                "id": f"wr:{mid}",
                "source": "wr",
                "eventId": mid,
                "leagueId": sport or "wr",
                "leagueName": ev_label,
                "leagueShort": ev_label.replace("Men's Sevens Series 2027", "SVNS 7s M").replace("Women's Sevens Series 2027", "SVNS 7s F"),
                "phase": m.get("eventPhase") or "",
                "group": group,
                "sport": sport,
                "status": status,
                "statusText": status_text,
                "isArgentina": is_arg,
                "venue": {
                    "name": ven.get("name") or "",
                    "city": ven.get("city") or "",
                    "country": ven.get("country") or "",
                },
                "odds": None,
                **times,
                "home": {
                    "name": h_name,
                    "abbr": tms[0].get("abbreviation") or "",
                    "wrId": str(tms[0].get("id") or ""),
                    "crestUrl": "",
                    "score": str(scores[0]) if status != "scheduled" and len(scores) >= 2 else None,
                    "lineup": [],
                    "coach": "",
                },
                "away": {
                    "name": a_name,
                    "abbr": tms[1].get("abbreviation") or "",
                    "wrId": str(tms[1].get("id") or ""),
                    "crestUrl": "",
                    "score": str(scores[1]) if status != "scheduled" and len(scores) >= 2 else None,
                    "lineup": [],
                    "coach": "",
                },
                "scoring": [],
                "cards": [],
                "officials": [],
            })
        if len(items) < 100:
            break
    return out


def merge_and_dedupe(espn_list: list[dict], wr_list: list[dict]) -> list[dict]:
    by_sig: dict[str, dict] = {}

    def sig(m: dict) -> str:
        day = (m.get("dateUtc") or "")[:10]
        t1 = re.sub(r"\s+7s$", "", norm_key(m["home"]["name"]))
        t2 = re.sub(r"\s+7s$", "", norm_key(m["away"]["name"]))
        return f"{day}::{'|'.join(sorted([t1, t2]))}"

    for m in espn_list:
        by_sig[sig(m)] = m

    for m in wr_list:
        s = sig(m)
        if s in by_sig:
            ex = by_sig[s]
            if not m["home"].get("crestUrl") and ex["home"].get("crestUrl"):
                m["home"]["crestUrl"] = ex["home"]["crestUrl"]
            if not m["away"].get("crestUrl") and ex["away"].get("crestUrl"):
                m["away"]["crestUrl"] = ex["away"]["crestUrl"]
            if not m.get("odds") and ex.get("odds"):
                m["odds"] = ex["odds"]
            m["espnPair"] = {"leagueId": ex["leagueId"], "eventId": ex["eventId"]}
            by_sig[s] = m
        else:
            by_sig[s] = m

    items = list(by_sig.values())

    def sort_key(m: dict):
        w = 0 if m["status"] == "live" else (1 if m["status"] == "scheduled" else 2)
        arg_boost = 0 if m.get("isArgentina") else 1
        ts = m.get("ts") or 0
        t_order = -ts if m["status"] == "finished" else ts
        return (w, arg_boost, t_order)

    items.sort(key=sort_key)
    return items


def enrich_match_details(matches: list[dict], conn: sqlite3.Connection) -> None:
    live_m = [m for m in matches if m["status"] == "live"]
    fin_m = [m for m in matches if m["status"] == "finished"]
    fin_m.sort(key=lambda m: (0 if m.get("isArgentina") else 1, -(m.get("ts") or 0)))
    up_arg_nat = [m for m in matches if m["status"] == "scheduled" and (m.get("isArgentina") or m["group"] in ("nations", "worldcup", "arg"))]
    priority_seen = set()
    priority = []
    for m in (live_m + fin_m[:55] + up_arg_nat[:30]):
        if m["id"] not in priority_seen:
            priority_seen.add(m["id"])
            priority.append(m)

    def fetch_detail(m: dict):
        if m["source"] == "wr":
            sm = fetch_json(f"https://api.wr-rims-prod.pulselive.com/rugby/v3/match/{m['eventId']}/summary", timeout=10)
            return m, "wr", sm
        else:
            sm = fetch_json(f"https://site.api.espn.com/apis/site/v2/sports/rugby/{m['leagueId']}/summary?event={m['eventId']}", timeout=10)
            return m, "espn", sm

    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as ex:
        for m, src, sm in ex.map(fetch_detail, priority):
            if not isinstance(sm, dict):
                continue
            if src == "wr":
                officials = []
                for off in sm.get("officials") or []:
                    o = off.get("official") or {}
                    nm = (o.get("name") or {}).get("display") or ""
                    pos = off.get("position") or ""
                    if nm:
                        officials.append({"name": nm, "role": pos, "country": o.get("country") or ""})
                m["officials"] = officials

                teams_arr = sm.get("teams") or []
                for idx, side_key in enumerate(["home", "away"]):
                    if idx >= len(teams_arr):
                        continue
                    t_block = teams_arr[idx]
                    tl = t_block.get("teamList") or {}
                    caps = set(str(x) for x in (tl.get("captainIds") or []))
                    lineup = []
                    for entry in tl.get("list") or []:
                        pl = entry.get("player") or {}
                        pid = str(pl.get("id") or "")
                        p_name = (pl.get("name") or {}).get("display") or ""
                        if not p_name:
                            continue
                        num_str = str(entry.get("number") or "")
                        num_int = int(num_str) if num_str.isdigit() else 99
                        is_sub = bool(pl.get("isReplacement")) or (num_int > 15 if m.get("sport") not in ("mrs", "wrs") else num_int > 7)
                        lineup.append({
                            "jersey": num_str or "-",
                            "name": p_name,
                            "position": translate_pos(entry.get("positionLabel") or ""),
                            "posCode": entry.get("position") or "",
                            "starter": not is_sub,
                            "captain": pid in caps,
                            "age": (pl.get("age") or {}).get("years"),
                            "height": pl.get("height"),
                            "weight": pl.get("weight"),
                            "caps": pl.get("caps"),
                        })
                    lineup.sort(key=lambda x: (0 if x["starter"] else 1, int(x["jersey"]) if str(x["jersey"]).isdigit() else 99))
                    m[side_key]["lineup"] = lineup

                    if lineup:
                        t_nk = norm_key(m[side_key]["name"])
                        conn.execute(
                            """INSERT INTO team_rosters(team_key, team_name, league_name, event_date, lineup_json, updated_at)
                               VALUES (?, ?, ?, ?, ?, ?)
                               ON CONFLICT(team_key) DO UPDATE SET
                                 lineup_json=CASE WHEN excluded.event_date >= team_rosters.event_date THEN excluded.lineup_json ELSE team_rosters.lineup_json END,
                                 event_date=CASE WHEN excluded.event_date >= team_rosters.event_date THEN excluded.event_date ELSE team_rosters.event_date END,
                                 updated_at=excluded.updated_at""",
                            (t_nk, m[side_key]["name"], m["leagueName"], m.get("dateUtc") or "", json.dumps(lineup, ensure_ascii=False), dt.datetime.now(UTC_TZ).strftime("%Y-%m-%dT%H:%M:%SZ")),
                        )

                    sc = t_block.get("scoring") or {}
                    for ev_type, label in [("Try", "Try"), ("Conversion", "Conv"), ("Penalty", "Penal"), ("DropGoal", "Drop")]:
                        for item in sc.get(ev_type) or []:
                            pl_name = ((item.get("player") or {}).get("name") or {}).get("display") or ""
                            min_lbl = (item.get("time") or {}).get("label") or ""
                            m["scoring"].append({
                                "team": side_key,
                                "teamName": m[side_key]["name"],
                                "type": label,
                                "player": pl_name,
                                "minute": min_lbl,
                            })
                    disc = t_block.get("disciplinary") or {}
                    for ctype, clabel in [("Yellow", "Amarilla"), ("Red", "Roja")]:
                        for item in disc.get(ctype) or []:
                            pl_name = ((item.get("player") or {}).get("name") or {}).get("display") or ""
                            min_lbl = (item.get("time") or {}).get("label") or ""
                            m["cards"].append({"team": side_key, "type": clabel, "player": pl_name, "minute": min_lbl})
            else:
                gi = sm.get("gameInfo") or {}
                ven = gi.get("venue") or {}
                if ven.get("fullName") and not m["venue"].get("name"):
                    addr = ven.get("address") or {}
                    m["venue"] = {"name": ven.get("fullName") or "", "city": addr.get("city") or "", "country": addr.get("state") or ""}
                for r_block in sm.get("rosters") or []:
                    side_key = "home" if r_block.get("homeAway") == "home" else "away"
                    lineup = []
                    for entry in r_block.get("roster") or []:
                        ath = entry.get("athlete") or {}
                        p_name = ath.get("displayName") or ath.get("fullName") or ""
                        if not p_name:
                            continue
                        num_str = str(entry.get("jersey") or "")
                        num_int = int(num_str) if num_str.isdigit() else 99
                        pos_obj = entry.get("position") or ath.get("position") or {}
                        pos_disp = pos_obj.get("displayName") or pos_obj.get("name") or ""
                        lineup.append({
                            "jersey": num_str or "-",
                            "name": p_name,
                            "position": translate_pos(pos_disp),
                            "posCode": pos_obj.get("abbreviation") or "",
                            "starter": bool(entry.get("starter")) or num_int <= 15,
                            "captain": bool(entry.get("captain")),
                        })
                    lineup.sort(key=lambda x: (0 if x["starter"] else 1, int(x["jersey"]) if str(x["jersey"]).isdigit() else 99))
                    m[side_key]["lineup"] = lineup
                    if lineup:
                        t_nk = norm_key(m[side_key]["name"])
                        conn.execute(
                            """INSERT INTO team_rosters(team_key, team_name, league_name, event_date, lineup_json, updated_at)
                               VALUES (?, ?, ?, ?, ?, ?)
                               ON CONFLICT(team_key) DO UPDATE SET
                                 lineup_json=CASE WHEN excluded.event_date >= team_rosters.event_date THEN excluded.lineup_json ELSE team_rosters.lineup_json END,
                                 event_date=CASE WHEN excluded.event_date >= team_rosters.event_date THEN excluded.event_date ELSE team_rosters.event_date END,
                                 updated_at=excluded.updated_at""",
                            (t_nk, m[side_key]["name"], m["leagueName"], m.get("dateUtc") or "", json.dumps(lineup, ensure_ascii=False), dt.datetime.now(UTC_TZ).strftime("%Y-%m-%dT%H:%M:%SZ")),
                        )
    conn.commit()


def fetch_rankings() -> dict:
    men = []
    women = []
    m_raw = fetch_json("https://api.wr-rims-prod.pulselive.com/rugby/v3/rankings/mru", timeout=10)
    if isinstance(m_raw, dict):
        for e in (m_raw.get("entries") or [])[:30]:
            tm = e.get("team") or {}
            men.append({
                "pos": e.get("pos"),
                "prevPos": e.get("previousPos"),
                "team": tm.get("name") or "",
                "abbr": tm.get("abbreviation") or "",
                "pts": round(float(e.get("pts") or 0), 2),
                "matches": e.get("matches"),
            })
    w_raw = fetch_json("https://api.wr-rims-prod.pulselive.com/rugby/v3/rankings/wru", timeout=10)
    if isinstance(w_raw, dict):
        for e in (w_raw.get("entries") or [])[:25]:
            tm = e.get("team") or {}
            women.append({
                "pos": e.get("pos"),
                "prevPos": e.get("previousPos"),
                "team": tm.get("name") or "",
                "abbr": tm.get("abbreviation") or "",
                "pts": round(float(e.get("pts") or 0), 2),
                "matches": e.get("matches"),
            })
    return {"men": men, "women": women}


def apply_team_meta_and_log(matches: list[dict], conn: sqlite3.Connection, rankings: dict) -> tuple[list[dict], int]:
    meta_rows = conn.execute("SELECT team_key, display_name, espn_id, crest_url, coach, stadium FROM team_meta").fetchall()
    meta_map = {r[0]: {"name": r[1], "espnId": r[2], "crestUrl": r[3], "coach": r[4], "stadium": r[5]} for r in meta_rows}
    roster_rows = conn.execute("SELECT team_key, team_name, league_name, event_date, lineup_json FROM team_rosters").fetchall()
    roster_map = {r[0]: {"name": r[1], "leagueName": r[2], "eventDate": r[3], "lineup": json.loads(r[4])} for r in roster_rows}

    rk_men_map = {norm_key(r["team"]): r["pos"] for r in rankings.get("men", [])}

    for m in matches:
        for side in ("home", "away"):
            tm = m[side]
            nk = norm_key(tm["name"])
            base_nk = re.sub(r"\s+7s$", "", nk)
            info = meta_map.get(nk) or meta_map.get(base_nk) or {}
            if not tm.get("crestUrl") and info.get("crestUrl"):
                tm["crestUrl"] = info["crestUrl"]
            if not tm.get("coach") and info.get("coach"):
                tm["coach"] = info["coach"]
            if nk in rk_men_map:
                tm["worldRank"] = rk_men_map[nk]
            if not tm["lineup"] and nk in roster_map and roster_map[nk]["lineup"]:
                tm["probableLineup"] = roster_map[nk]["lineup"]
                tm["probableFromDate"] = roster_map[nk]["eventDate"][:10]

    now_iso = dt.datetime.now(UTC_TZ).strftime("%Y-%m-%dT%H:%M:%SZ")
    new_logged = 0
    for m in matches:
        if m["status"] == "finished" and m["home"]["score"] is not None and m["away"]["score"] is not None:
            exists = conn.execute("SELECT 1 FROM match_history WHERE match_key=?", (m["id"],)).fetchone()
            if not exists:
                new_logged += 1
            conn.execute(
                """INSERT INTO match_history(match_key, source, league_id, league_name, group_key, date_utc, date_arg, home_team, away_team, home_score, away_score, venue, city, status, updated_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                   ON CONFLICT(match_key) DO UPDATE SET
                     home_score=excluded.home_score,
                     away_score=excluded.away_score,
                     updated_at=excluded.updated_at""",
                (
                    m["id"],
                    m["source"],
                    m["leagueId"],
                    m["leagueName"],
                    m["group"],
                    m.get("dateUtc") or "",
                    m.get("dateArg") or "",
                    m["home"]["name"],
                    m["away"]["name"],
                    int(m["home"]["score"]) if str(m["home"]["score"]).isdigit() else 0,
                    int(m["away"]["score"]) if str(m["away"]["score"]).isdigit() else 0,
                    m["venue"].get("name") or "",
                    m["venue"].get("city") or "",
                    m["status"],
                    now_iso,
                ),
            )
    conn.commit()

    directory = []
    seen = set()
    for nk, info in meta_map.items():
        if nk in seen or not info.get("crestUrl"):
            continue
        seen.add(nk)
        r_info = roster_map.get(nk)
        directory.append({
            "key": nk,
            "name": info["name"],
            "crestUrl": info["crestUrl"],
            "coach": info.get("coach") or "",
            "stadium": info.get("stadium") or "",
            "worldRank": rk_men_map.get(nk),
            "roster": r_info["lineup"] if r_info else [],
            "rosterUpdated": r_info["eventDate"][:10] if r_info else "",
            "isArgentina": ("argentina" in nk or nk in ("newman", "hindu", "sic", "casi", "belgrano ac", "cuba", "alumni", "regatas bella vista", "b.a.c.r.c.", "san luis", "champagnat", "club atletico del rosario")),
        })
    directory.sort(key=lambda x: (0 if x["isArgentina"] else 1, x["worldRank"] or 999, 0 if x["roster"] else 1, x["name"]))
    return directory, new_logged


def main() -> None:
    now_utc = dt.datetime.now(UTC_TZ)
    conn = init_db()

    team_index = harvest_espn_teams(conn)
    espn_matches = parse_espn_matches(now_utc)
    wr_matches = parse_world_rugby_matches(now_utc)
    matches = merge_and_dedupe(espn_matches, wr_matches)

    enrich_match_details(matches, conn)

    names_to_enrich = list(WIKI_PAGE_MAP.keys())
    for m in matches:
        names_to_enrich.append(m["home"]["name"])
        names_to_enrich.append(m["away"]["name"])
    enrich_team_meta_from_wiki(conn, names_to_enrich, team_index)

    rankings = fetch_rankings()
    meta_rows = {r[0]: r[1] for r in conn.execute("SELECT team_key, crest_url FROM team_meta").fetchall()}
    for cat in ("men", "women"):
        for r in rankings.get(cat, []):
            r["crestUrl"] = meta_rows.get(norm_key(r["team"]), "")

    teams_dir, new_logged = apply_team_meta_and_log(matches, conn, rankings)

    history_rows = conn.execute(
        "SELECT match_key, league_name, group_key, date_arg, home_team, away_team, home_score, away_score, venue, city FROM match_history ORDER BY date_utc DESC LIMIT 120"
    ).fetchall()
    history_list = [
        {
            "id": r[0],
            "leagueName": r[1],
            "group": r[2],
            "dateArg": r[3],
            "homeTeam": r[4],
            "awayTeam": r[5],
            "homeScore": r[6],
            "awayScore": r[7],
            "venue": r[8],
            "city": r[9],
        }
        for r in history_rows
    ]

    status_counts = {
        "live": sum(1 for m in matches if m["status"] == "live"),
        "scheduled": sum(1 for m in matches if m["status"] == "scheduled"),
        "finished": sum(1 for m in matches if m["status"] == "finished"),
    }
    group_counts = {}
    for m in matches:
        group_counts[m["group"]] = group_counts.get(m["group"], 0) + 1

    arg_matches = [m for m in matches if m.get("isArgentina")]
    arg_next = next((m for m in arg_matches if m["status"] in ("live", "scheduled")), arg_matches[0] if arg_matches else None)
    arg_next_xv = next((m for m in arg_matches if m["status"] in ("live", "scheduled") and m["group"] != "sevens"), None)
    arg_next_7s = next((m for m in arg_matches if m["status"] in ("live", "scheduled") and m["group"] == "sevens"), None)

    total_history = conn.execute("SELECT COUNT(*) FROM match_history").fetchone()[0]
    total_rosters = conn.execute("SELECT COUNT(*) FROM team_rosters").fetchone()[0]

    payload = {
        "engineVersion": "1.0.0-RUGBY-PRO",
        "generatedAt": now_utc.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "generatedAtArg": format_arg_times(now_utc)["dateArg"],
        "totalMatches": len(matches),
        "statusCounts": status_counts,
        "groupCounts": group_counts,
        "argentinaNext": arg_next,
        "argentinaNextXV": arg_next_xv,
        "argentinaNext7s": arg_next_7s,
        "argentinaMatches": arg_matches,
        "matches": matches,
        "teamsDirectory": teams_dir,
        "rankings": rankings,
        "history": history_list,
        "dbStats": {
            "totalLoggedMatches": total_history,
            "newHistoryRows": new_logged,
            "totalRosters": total_rosters,
        },
    }

    for path in (OUT_DATA_JSON, OUT_ROOT_JSON):
        with open(path, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False)

    with open(OUT_HISTORY_JSON, "w", encoding="utf-8") as f:
        json.dump({"updatedAt": payload["generatedAt"], "history": history_list, "teams": teams_dir[:80]}, f, ensure_ascii=False)

    with open(OUT_REPORT_MD, "w", encoding="utf-8") as f:
        f.write(
            f"# Rugby Intelligence Pro — Sync Report\n\n"
            f"- Generated: `{payload['generatedAtArg']}`\n"
            f"- Total Matches: **{len(matches)}** (`live: {status_counts['live']}`, `scheduled: {status_counts['scheduled']}`, `finished: {status_counts['finished']}`)\n"
            f"- History DB: **{total_history}** logged matches | **{total_rosters}** team rosters | **{len(teams_dir)}** verified crests\n"
        )

    print(
        json.dumps(
            {
                "ok": True,
                "matches": len(matches),
                "statusCounts": status_counts,
                "groupCounts": group_counts,
                "argMatches": len(arg_matches),
                "teamsDirectory": len(teams_dir),
                "totalLoggedMatches": total_history,
                "totalRosters": total_rosters,
                "argNextXV": (arg_next_xv["home"]["name"] + " vs " + arg_next_xv["away"]["name"] + " (" + arg_next_xv["dateArg"] + ")") if arg_next_xv else None,
                "argNext7s": (arg_next_7s["home"]["name"] + " vs " + arg_next_7s["away"]["name"] + " (" + arg_next_7s["dateArg"] + ")") if arg_next_7s else None,
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
