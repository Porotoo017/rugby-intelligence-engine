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
import xml.etree.ElementTree as ET
from zoneinfo import ZoneInfo

try:
    from crests_registry import get_country_flag_url, is_broken_crest, resolve_crest_url
except ImportError:
    from engine.crests_registry import get_country_flag_url, is_broken_crest, resolve_crest_url

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DATA_DIR = os.path.join(ROOT, "data")
os.makedirs(DATA_DIR, exist_ok=True)
DB_PATH = os.path.join(DATA_DIR, "rugby_master.db")
OUT_DATA_JSON = os.path.join(DATA_DIR, "rugby_engine_output.json")
OUT_ROOT_JSON = os.path.join(ROOT, "rugby_engine_output.json")
OUT_HISTORY_JSON = os.path.join(DATA_DIR, "rugby_teams_history.json")
OUT_REPORT_MD = os.path.join(ROOT, "REPORT_ACTUAL.md")
OUT_COMPETITIONS_JSON = os.path.join(DATA_DIR, "rugby_competitions.json")

ARG_TZ = ZoneInfo("America/Argentina/Buenos_Aires")
UTC_TZ = dt.timezone.utc
USER_AGENT = "RugbyIntelligencePro/1.0 (+https://github.com/Porotoo017/rugby-intelligence-engine)"

ESPN_LEAGUES = [
    {"id": "289279", "name": "URBA Top 14", "group": "urba", "short": "URBA Top 14", "compId": "ar-urba-top14", "category": "Clubes"},
    {"id": "2009", "name": "URBA Primera A", "group": "urba", "short": "URBA 1ª A", "compId": "ar-urba-top14", "category": "Clubes"},
    {"id": "17567", "name": "Nations Championship", "group": "nations", "short": "Nations Champ", "compId": "int-nations-tests", "category": "Selecciones"},
    {"id": "244293", "name": "The Rugby Championship", "group": "nations", "short": "Rugby Champ", "compId": "trc-rugby-championship", "category": "Selecciones"},
    {"id": "180659", "name": "Six Nations", "group": "nations", "short": "6 Naciones", "compId": "eu-six-nations", "category": "Selecciones"},
    {"id": "289234", "name": "International Test Matches", "group": "nations", "short": "Test Match", "compId": "int-nations-tests", "category": "Selecciones"},
    {"id": "268565", "name": "British & Irish Lions Tour", "group": "nations", "short": "Lions Tour", "compId": "int-nations-tests", "category": "Selecciones"},
    {"id": "164205", "name": "Rugby World Cup", "group": "worldcup", "short": "Mundial RWC", "compId": "rwc-world-cup", "category": "Selecciones"},
    {"id": "289237", "name": "Women's Rugby World Cup", "group": "worldcup", "short": "Mundial Fem", "compId": "rwc-world-cup", "category": "Selecciones"},
    {"id": "282", "name": "Olympic Men's 7s", "group": "sevens", "short": "Seven Olímpico", "compId": "wr-svns", "category": "Selecciones"},
    {"id": "283", "name": "Olympic Women's 7s", "group": "sevens", "short": "Seven Fem", "compId": "wr-svns", "category": "Selecciones"},
    {"id": "271937", "name": "European Champions Cup", "group": "copas", "short": "Champions Cup", "compId": "epcr-champions-cup", "category": "Copas"},
    {"id": "272073", "name": "European Challenge Cup", "group": "copas", "short": "Challenge Cup", "compId": "epcr-challenge-cup", "category": "Copas"},
    {"id": "270559", "name": "French Top 14", "group": "top14", "short": "Top 14 Francia", "compId": "fr-top14", "category": "Clubes"},
    {"id": "270557", "name": "United Rugby Championship", "group": "clubs", "short": "URC", "compId": "eu-urc", "category": "Clubes"},
    {"id": "267979", "name": "Gallagher Premiership", "group": "clubs", "short": "Premiership", "compId": "eng-premiership", "category": "Clubes"},
    {"id": "242041", "name": "Super Rugby Pacific", "group": "clubs", "short": "Super Rugby Pacific", "compId": "oce-super-rugby-pacific", "category": "Clubes"},
    {"id": "270563", "name": "NPC Mitre 10 Cup", "group": "clubs", "short": "NPC NZ", "compId": "nz-npc", "category": "Clubes"},
    {"id": "289262", "name": "Major League Rugby", "group": "clubs", "short": "MLR USA", "compId": "us-mlr", "category": "Clubes"},
]

COMPETITIONS_SCHEMA_SQL = """CREATE TABLE IF NOT EXISTS competitions (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    short_name TEXT NOT NULL,
    region TEXT NOT NULL,
    category TEXT NOT NULL,
    group_key TEXT NOT NULL,
    source TEXT NOT NULL,
    external_id TEXT,
    teams_count INTEGER NOT NULL,
    teams_note TEXT,
    season_window TEXT NOT NULL,
    is_argentina_core INTEGER DEFAULT 0,
    sort_order INTEGER DEFAULT 50,
    matches_count INTEGER DEFAULT 0,
    updated_at TEXT
);"""

COMPETITIONS_CATALOG = [
    {
        "id": "ar-urba-top14",
        "name": "URBA Top 14 (Buenos Aires)",
        "shortName": "URBA Top 14",
        "region": "Buenos Aires, Argentina",
        "category": "Clubes",
        "groupKey": "urba",
        "source": "urba",
        "externalId": "289279",
        "teamsCount": 12,
        "teamsNote": "12 en Top 14 · 99 clubes en 8 divisiones",
        "season": "Marzo – Noviembre",
        "isArgentinaCore": True,
        "sortOrder": 1,
        "standingsLeagueId": None,
        "notes": "Base argentina preservada intacta (URBA oficial).",
    },
    {
        "id": "ar-los-pumas",
        "name": "Los Pumas XV & Los Pumas 7s (UAR)",
        "shortName": "Los Pumas",
        "region": "Argentina / Internacional",
        "category": "Selecciones",
        "groupKey": "arg",
        "source": "espn+wr",
        "externalId": "uar",
        "teamsCount": 2,
        "teamsNote": "Selección Mayor XV & Circuito Mundial Seven",
        "season": "Todo el año (Jul/Ago–Nov & SVNS)",
        "isArgentinaCore": True,
        "sortOrder": 2,
        "standingsLeagueId": None,
        "notes": "Cobertura completa UAR intacta.",
    },
    {
        "id": "fr-top14",
        "name": "Top 14 (Ligue Nationale de Rugby)",
        "shortName": "Top 14 Francia",
        "region": "Francia",
        "category": "Clubes",
        "groupKey": "top14",
        "source": "espn+wr",
        "externalId": "270559",
        "teamsCount": 14,
        "teamsNote": "14 clubes profesionales de Francia",
        "season": "Septiembre – Junio",
        "seasonParam": "2027",
        "isArgentinaCore": False,
        "sortOrder": 10,
        "standingsLeagueId": "270559",
        "notes": "Diferenciado explícitamente del Top 14 de la URBA (Buenos Aires).",
    },
    {
        "id": "eu-urc",
        "name": "United Rugby Championship (URC)",
        "shortName": "URC",
        "region": "Irlanda, Gales, Escocia, Italia y Sudáfrica",
        "category": "Clubes",
        "groupKey": "clubs",
        "source": "espn+wr",
        "externalId": "270557",
        "teamsCount": 16,
        "teamsNote": "16 franquicias (4 IRL, 4 WAL, 4 RSA, 2 SCO, 2 ITA)",
        "season": "Septiembre – Junio",
        "isArgentinaCore": False,
        "sortOrder": 11,
        "standingsLeagueId": "270557",
        "notes": "Liga intercontinental de clubes y franquicias del Hemisferio Norte y Sudáfrica.",
    },
    {
        "id": "eng-premiership",
        "name": "Premiership Rugby (Gallagher Premiership)",
        "shortName": "Premiership",
        "region": "Inglaterra",
        "category": "Clubes",
        "groupKey": "clubs",
        "source": "espn+wr",
        "externalId": "267979",
        "teamsCount": 10,
        "teamsNote": "10 clubes históricos de Inglaterra",
        "season": "Septiembre – Junio",
        "isArgentinaCore": False,
        "sortOrder": 12,
        "standingsLeagueId": "267979",
        "notes": "Primera división profesional del rugby inglés.",
    },
    {
        "id": "oce-super-rugby-pacific",
        "name": "Super Rugby Pacific",
        "shortName": "Super Rugby Pacific",
        "region": "Oceanía (NZ, Australia, Fiji, Islas del Pacífico)",
        "category": "Clubes",
        "groupKey": "clubs",
        "source": "espn",
        "externalId": "242041",
        "teamsCount": 11,
        "teamsNote": "11 franquicias (5 NZL, 4 AUS, Fijian Drua, Moana Pasifika)",
        "season": "Febrero – Junio",
        "isArgentinaCore": False,
        "sortOrder": 13,
        "standingsLeagueId": "242041",
        "notes": "Máxima competencia de franquicias de Oceanía.",
    },
    {
        "id": "sa-super-rugby-americas",
        "name": "Súper Rugby Américas (SRA)",
        "shortName": "Súper Rugby Américas",
        "region": "Sudamérica (ARG, URU, CHI, PAR, BRA)",
        "category": "Clubes",
        "groupKey": "sra",
        "source": "sudamerica-rugby",
        "externalId": "regisar-11",
        "teamsCount": 8,
        "teamsNote": "8 franquicias (4 ARG, 1 URU, 1 CHI, 1 PAR, 1 BRA)",
        "season": "Febrero – Junio",
        "isArgentinaCore": False,
        "sortOrder": 14,
        "standingsLeagueId": None,
        "notes": "Conexión sudamericana oficial: Pampas, Dogos XV, Capibaras XV, Tarucas, Peñarol, Selknam, Yacare XV y Cobras.",
    },
    {
        "id": "epcr-champions-cup",
        "name": "Investec Champions Cup",
        "shortName": "Champions Cup",
        "region": "Europa y Sudáfrica (EPCR)",
        "category": "Copas",
        "groupKey": "copas",
        "source": "espn+wr",
        "externalId": "271937",
        "teamsCount": 24,
        "teamsNote": "24 clasificados (Top 14, URC y Premiership)",
        "season": "Diciembre – Mayo",
        "isArgentinaCore": False,
        "sortOrder": 20,
        "standingsLeagueId": "271937",
        "notes": "Máxima copa continental de clubes organizada por EPCR.",
    },
    {
        "id": "epcr-challenge-cup",
        "name": "EPCR Challenge Cup",
        "shortName": "Challenge Cup",
        "region": "Europa, Sudáfrica e Invitados (EPCR)",
        "category": "Copas",
        "groupKey": "copas",
        "source": "espn+wr",
        "externalId": "272073",
        "teamsCount": 18,
        "teamsNote": "18 clubes en 3 grupos + cruces desde Champions Cup",
        "season": "Diciembre – Mayo",
        "isArgentinaCore": False,
        "sortOrder": 21,
        "standingsLeagueId": "272073",
        "notes": "Segunda copa continental europea con participación de franquicias sudafricanas e invitadas.",
    },
    {
        "id": "trc-rugby-championship",
        "name": "The Rugby Championship",
        "shortName": "Rugby Championship",
        "region": "Hemisferio Sur — SANZAAR (ARG, NZL, RSA, AUS)",
        "category": "Selecciones",
        "groupKey": "nations",
        "source": "espn",
        "externalId": "244293",
        "teamsCount": 4,
        "teamsNote": "4 potencias del Sur: Los Pumas, All Blacks, Springboks y Wallabies",
        "season": "Agosto – Octubre",
        "isArgentinaCore": False,
        "sortOrder": 30,
        "standingsLeagueId": "244293",
        "notes": "Torneo anual del Hemisferio Sur donde compiten Los Pumas.",
    },
    {
        "id": "eu-six-nations",
        "name": "Torneo de las Seis Naciones (Six Nations)",
        "shortName": "Seis Naciones",
        "region": "Europa (ENG, FRA, IRL, SCO, WAL, ITA)",
        "category": "Selecciones",
        "groupKey": "nations",
        "source": "espn+wr",
        "externalId": "180659",
        "teamsCount": 6,
        "teamsNote": "6 selecciones históricas de Europa",
        "season": "Febrero – Marzo",
        "isArgentinaCore": False,
        "sortOrder": 31,
        "standingsLeagueId": "180659",
        "notes": "Campeonato anual del Hemisferio Norte.",
    },
    {
        "id": "rwc-world-cup",
        "name": "Copa del Mundo de Rugby (Rugby World Cup)",
        "shortName": "Mundial RWC",
        "region": "Mundial (World Rugby)",
        "category": "Selecciones",
        "groupKey": "worldcup",
        "source": "espn+wr",
        "externalId": "164205",
        "teamsCount": 24,
        "teamsNote": "24 selecciones desde RWC 2027 (20 hasta RWC 2023)",
        "season": "Octubre – Noviembre (cada 4 años)",
        "isArgentinaCore": False,
        "sortOrder": 32,
        "standingsLeagueId": "164205",
        "notes": "Máximo torneo mundial de selecciones organizado por World Rugby.",
    },
]

SRA_FRANCHISES = [
    {"key": "dogos xv", "name": "Dogos XV", "abbr": "DOG", "country": "Argentina", "flag": "ar", "city": "Córdoba", "stadium": "Tala Rugby Club / Córdoba Athletic", "coach": "Diego Ghiglione", "crestUrl": "https://dle22gvyr6oyt.cloudfront.net/images/img_4804-1.png"},
    {"key": "pampas", "name": "Pampas", "abbr": "PAM", "country": "Argentina", "flag": "ar", "city": "Buenos Aires", "stadium": "La Catedral (CASI)", "coach": "Juan Manuel Leguizamón", "crestUrl": "https://dle22gvyr6oyt.cloudfront.net/images/pampas_xv_logo-1.png"},
    {"key": "capibaras xv", "name": "Capibaras XV", "abbr": "CAP", "country": "Argentina", "flag": "ar", "city": "Rosario", "stadium": "Jockey Club de Rosario / Hipódromo", "coach": "Nicolás Galatro", "crestUrl": "https://dle22gvyr6oyt.cloudfront.net/images/capibaras_xv_logo.png"},
    {"key": "tarucas", "name": "Tarucas", "abbr": "TAR", "country": "Argentina", "flag": "ar", "city": "Tucumán", "stadium": "La Caldera del Parque (Tucumán LT)", "coach": "Álvaro Galindo", "crestUrl": "https://dle22gvyr6oyt.cloudfront.net/images/tarucas_-original-1.png"},
    {"key": "penarol rugby", "name": "Peñarol Rugby", "abbr": "PEN", "country": "Uruguay", "flag": "uy", "city": "Montevideo", "stadium": "Estadio Charrúa", "coach": "Ivo Dugonjic", "crestUrl": "https://dle22gvyr6oyt.cloudfront.net/images/l_af25ccf9-c195-42b1-906f-29fe2a174509_20250306033543830.png"},
    {"key": "selknam", "name": "Selknam", "abbr": "SEL", "country": "Chile", "flag": "cl", "city": "Santiago", "stadium": "Estadio Municipal de La Pintana", "coach": "Jake Mangin", "crestUrl": "https://dle22gvyr6oyt.cloudfront.net/images/selknam-1.png"},
    {"key": "yacare xv", "name": "Yacare XV", "abbr": "YAC", "country": "Paraguay", "flag": "py", "city": "Luque / Asunción", "stadium": "Estadio Héroes de Curupayty", "coach": "Ramiro Pemán", "crestUrl": "https://dle22gvyr6oyt.cloudfront.net/images/yacarexv-1-1.png"},
    {"key": "cobras brasil rugby", "name": "Cobras Brasil Rugby", "abbr": "COB", "country": "Brasil", "flag": "br", "city": "Jacareí / São Paulo", "stadium": "Estádio Du Cambusano", "coach": "Josh Reeves", "crestUrl": "https://dle22gvyr6oyt.cloudfront.net/images/155_logo-cobras-2024_cobras-amarelo-v.png"},
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
    "bordeaux begles": "Union_Bordeaux_Bègles",
    "stade rochelais": "Stade_Rochelais",
    "la rochelle": "Stade_Rochelais",
    "racing 92": "Racing_92",
    "stade francais paris": "Stade_Français",
    "stade francais": "Stade_Français",
    "rc toulon": "RC_Toulon",
    "toulon": "RC_Toulon",
    "section paloise": "Section_Paloise",
    "pau": "Section_Paloise",
    "aviron bayonnais": "Aviron_Bayonnais",
    "bayonne": "Aviron_Bayonnais",
    "montpellier herault rugby club": "Montpellier_Hérault_Rugby",
    "montpellier herault": "Montpellier_Hérault_Rugby",
    "castres olympique": "Castres_Olympique",
    "lyon olympique universitaire": "Lyon_OU",
    "lyon": "Lyon_OU",
    "asm clermont auvergne": "ASM_Clermont_Auvergne",
    "clermont auvergne": "ASM_Clermont_Auvergne",
    "usa perpignan": "USA_Perpignan",
    "perpignan": "USA_Perpignan",
    "rc vannes": "RC_Vannes",
    "vannes": "RC_Vannes",
    "montauban": "US_Montauban",
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
    day_str = f"{arg_dt.day:02d}/{arg_dt.month:02d}/{arg_dt.year}"
    time_str = f"{arg_dt.hour:02d}:{arg_dt.minute:02d}"
    return {
        "dateUtc": utc_dt.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "dateArg": f"{day_str} · {time_str}",
        "dayArg": day_str,
        "timeArg": time_str,
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
        CREATE TABLE IF NOT EXISTS competitions (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            short_name TEXT NOT NULL,
            region TEXT NOT NULL,
            category TEXT NOT NULL,
            group_key TEXT NOT NULL,
            source TEXT NOT NULL,
            external_id TEXT,
            teams_count INTEGER NOT NULL,
            teams_note TEXT,
            season_window TEXT NOT NULL,
            is_argentina_core INTEGER DEFAULT 0,
            sort_order INTEGER DEFAULT 50,
            matches_count INTEGER DEFAULT 0,
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
                if "some say" in val.lower(): val = val.split("Some say")[0].strip()
                if val.lower() not in ("vacant", "tbc", "tba", "none"):
                    coach = re.sub(r"\s*\[[^\]]*\]", "", val).strip()[:60]
            if not stadium and any(x in k for x in ("ground", "stadium", "home ground", "estadio")):
                stadium = re.sub(r"\s+\)", ")", re.sub(r"\s*\[[^\]]*\]", "", val)).strip()[:100]
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
                crest = clean_url(logos[0].get("href")) if logos else ""
                if not crest and tid and tid not in ("25912", "25920"):
                    crest = f"https://a.espncdn.com/i/teamlogos/rugby/teams/500/{tid}.png"
                if "countries/500/usa" in crest and "usa" not in name.lower():
                    crest = ""
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

        if lookups >= 50:
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
        if "countries/500/usa" in existing_crest and "usa" not in nk:
            existing_crest = ""
        if nk in ("bayonne", "perpignan") and w_crest:
            existing_crest = w_crest
        final_crest = existing_crest or w_crest or ""

        conn.execute(
            """INSERT INTO team_meta(team_key, display_name, espn_id, crest_url, coach, stadium, country, wiki_title, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT(team_key) DO UPDATE SET
                 crest_url=CASE WHEN excluded.crest_url != '' AND (team_meta.crest_url='' OR team_meta.crest_url LIKE '%countries/500/usa%' OR team_meta.crest_url LIKE '%25912.png') THEN excluded.crest_url ELSE team_meta.crest_url END,
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
    if "top 14" in low and "urba" not in low:
        return "top14"
    if any(k in low for k in ("urba", "buenos aires")):
        return "urba"
    if any(k in low for k in ("super rugby americas", "súper rugby américas")):
        return "sra"
    if "nacional de clubes" in low:
        return "arg"
    if any(k in low for k in ("champions cup", "challenge cup", "epcr")):
        return "copas"
    if any(k in low for k in ("nations", "six nations", "rugby championship", "international", "lions tour", "pacific nations", "bledisloe", "wxv", "rugby europe")):
        return "nations"
    return "clubs"


def classify_comp_id(league_name: str, group: str, league_id: str = "") -> tuple[str, str]:
    low = (league_name or "").lower()
    lid = str(league_id or "")
    if group == "urba" or "urba" in low or lid in ("289279", "2009"):
        return "ar-urba-top14", "Clubes"
    if group == "top14" or ("top 14" in low and "urba" not in low) or lid == "270559":
        return "fr-top14", "Clubes"
    if group == "sra" or "super rugby americas" in low or "súper rugby américas" in low:
        return "sa-super-rugby-americas", "Clubes"
    if "united rugby championship" in low or low.startswith("urc") or " urc" in low or lid == "270557":
        return "eu-urc", "Clubes"
    if lid == "267979" or ("premiership" in low and "women" not in low and "cup" not in low):
        return "eng-premiership", "Clubes"
    if "super rugby pacific" in low or lid == "242041":
        return "oce-super-rugby-pacific", "Clubes"
    if "champions cup" in low or lid == "271937":
        return "epcr-champions-cup", "Copas"
    if "challenge cup" in low or lid == "272073":
        return "epcr-challenge-cup", "Copas"
    if group == "copas":
        return "epcr-champions-cup", "Copas"
    if "rugby championship" in low or lid == "244293":
        return "trc-rugby-championship", "Selecciones"
    if "six nations" in low or "seis naciones" in low or lid == "180659":
        return "eu-six-nations", "Selecciones"
    if group == "worldcup" or "world cup" in low or lid in ("164205", "289237"):
        return "rwc-world-cup", "Selecciones"
    if group == "sevens":
        return "wr-svns", "Selecciones"
    if group in ("nations", "arg"):
        return "int-nations-tests", "Selecciones"
    return "int-clubs-other", "Clubes"


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
            past_win = 28 if lg["id"] == "270559" else 10
            if cdt and (now_utc - dt.timedelta(days=past_win)) <= cdt <= (now_utc + dt.timedelta(days=35)):
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
                    "compId": lg.get("compId") or classify_comp_id(lg["name"], lg["group"], lg["id"])[0],
                    "category": lg.get("category") or classify_comp_id(lg["name"], lg["group"], lg["id"])[1],
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
                        "crestUrl": ("" if (("perpignan" in h_name.lower() and "countries/500/usa" in str(h_tm.get("logo") or "")) or str(h_tm.get("id") or "") in ("25912", "25920")) else clean_url(h_tm.get("logo") or (f"https://a.espncdn.com/i/teamlogos/rugby/teams/500/{h_tm.get('id')}.png" if h_tm.get("id") else ""))),
                        "score": home.get("score") if status != "scheduled" else None,
                        "lineup": [],
                        "coach": "",
                    },
                    "away": {
                        "name": a_name,
                        "abbr": a_tm.get("abbreviation") or "",
                        "espnId": str(a_tm.get("id") or ""),
                        "crestUrl": ("" if (("perpignan" in a_name.lower() and "countries/500/usa" in str(a_tm.get("logo") or "")) or str(a_tm.get("id") or "") in ("25912", "25920")) else clean_url(a_tm.get("logo") or (f"https://a.espncdn.com/i/teamlogos/rugby/teams/500/{a_tm.get('id')}.png" if a_tm.get("id") else ""))),
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
                "phase": re.sub(r"\bPool\s+Pool\b", "Pool", str(m.get("eventPhase") or "")).strip(),
                "group": group,
                "compId": classify_comp_id(ev_label, group, sport)[0],
                "category": classify_comp_id(ev_label, group, sport)[1],
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
    fin_top14 = [m for m in fin_m if m["group"] == "top14"]
    up_arg_nat = [m for m in matches if m["status"] == "scheduled" and (m.get("isArgentina") or m["group"] in ("nations", "worldcup", "arg"))]
    priority_seen = set()
    priority = []
    for m in (live_m + fin_m[:55] + fin_top14 + up_arg_nat[:30]):
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
            base_nk = re.sub(r"\s+(women\s+7s|women|7s)$", "", nk).strip()
            info = meta_map.get(nk) or meta_map.get(base_nk) or {}
            resolved = resolve_crest_url(nk, str(tm.get("crestUrl") or ""), meta_map)
            tm["crestUrl"] = resolved
            flag_u = get_country_flag_url(nk)
            if flag_u:
                tm["flagUrl"] = flag_u
            if not tm.get("coach") and info.get("coach"):
                c_val = re.sub(r"\s*\[[^\]]*\]", "", str(info["coach"])).strip()
                if c_val.lower() not in ("vacant", "tbc", "tba", "none", "n/a", "-"):
                    tm["coach"] = c_val
            if nk in rk_men_map:
                tm["worldRank"] = rk_men_map[nk]
            elif base_nk in rk_men_map:
                tm["worldRank"] = rk_men_map[base_nk]
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

    team_groups: dict[str, set] = {}
    team_leagues: dict[str, set] = {}
    team_comps: dict[str, set] = {}
    for m in matches:
        grp = m.get("group") or ""
        lg_short = m.get("leagueShort") or m.get("leagueName") or ""
        cid = m.get("compId") or ""
        for side in ("home", "away"):
            tnk = norm_key(m[side]["name"])
            if grp:
                team_groups.setdefault(tnk, set()).add(grp)
            if lg_short:
                team_leagues.setdefault(tnk, set()).add(lg_short)
            if cid:
                team_comps.setdefault(tnk, set()).add(cid)
    for fr in SRA_FRANCHISES:
        team_groups.setdefault(fr["key"], set()).add("sra")
        team_leagues.setdefault(fr["key"], set()).add("Súper Rugby Américas")
        team_comps.setdefault(fr["key"], set()).add("sa-super-rugby-americas")

    directory = []
    seen = set()
    # Asegurar que también entren al directorio los clubes/selecciones de partidos sin fila previa en team_meta
    for m in matches:
        for side in ("home", "away"):
            t_name = m[side]["name"]
            tnk = norm_key(t_name)
            if tnk not in meta_map and not any(x in tnk for x in ("pool ", "loser ", "winner ")):
                cu = resolve_crest_url(tnk, m[side].get("crestUrl") or "", meta_map)
                if cu:
                    meta_map[tnk] = {
                        "name": t_name,
                        "espnId": m[side].get("espnId") or "",
                        "crestUrl": cu,
                        "coach": m[side].get("coach") or "",
                        "stadium": "",
                    }

    for nk, info in meta_map.items():
        cu = resolve_crest_url(nk, info.get("crestUrl") or "", meta_map)
        if nk in seen or not cu or is_broken_crest(cu, nk):
            continue
        seen.add(nk)
        r_info = roster_map.get(nk)
        grps = sorted(team_groups.get(nk, set()))
        lgs = sorted(team_leagues.get(nk, set()))
        flag_u = get_country_flag_url(nk)
        directory.append({
            "key": nk,
            "name": info["name"],
            "crestUrl": cu,
            "flagUrl": flag_u,
            "coach": "" if str(info.get("coach") or "").strip().lower() in ("vacant", "tbc", "tba", "none") else re.sub(r"\s*\[[^\]]*\]", "", str(info.get("coach") or "")).strip(),
            "stadium": re.sub(r"\s{2,}", " ", re.sub(r"\s+\)", ")", re.sub(r"\s*\[[^\]]*\]", "", str(info.get("stadium") or "")))).strip(),
            "worldRank": rk_men_map.get(nk),
            "groups": grps,
            "leagues": lgs,
            "compIds": sorted(team_comps.get(nk, set())),
            "roster": r_info["lineup"] if r_info else [],
            "rosterUpdated": (f"{r_info['eventDate'][8:10]}/{r_info['eventDate'][5:7]}/{r_info['eventDate'][:4]}" if r_info and len(r_info.get("eventDate", "")) >= 10 else ""),
            "isArgentina": ("argentina" in nk or nk in ("newman", "hindu", "sic", "casi", "belgrano ac", "cuba", "alumni", "regatas bella vista", "b.a.c.r.c.", "san luis", "champagnat", "club atletico del rosario", "pampas", "dogos xv", "tarucas", "capibaras xv")),
        })
    directory.sort(key=lambda x: (0 if x["isArgentina"] else 1, x["worldRank"] or 999, 0 if x["roster"] else 1, x["name"]))
    return directory, new_logged



URBA_CHAMPIONSHIPS = [
    {"id": 2025176, "name": "URBA Top 14 — Superior", "short": "Top 14"},
    {"id": 2025177, "name": "URBA Primera A — Superior", "short": "Primera A"},
    {"id": 2025178, "name": "URBA Primera B — Superior", "short": "Primera B"},
    {"id": 2025179, "name": "URBA Primera C — Superior", "short": "Primera C"},
    {"id": 2025180, "name": "URBA Segunda — Superior", "short": "Segunda"},
    {"id": 2025181, "name": "URBA Tercera — Superior", "short": "Tercera"},
    {"id": 2025182, "name": "URBA Desarrollo — Superior", "short": "Desarrollo"},
    {"id": 2025208, "name": "URBA Femenino — Top 9", "short": "Femenino"},
]


def fetch_text(url: str, timeout: int = 12) -> str:
    try:
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.read().decode("utf-8", errors="replace")
    except Exception:
        return ""


def strip_html(raw: str) -> str:
    txt = html.unescape(re.sub(r"<[^>]+>", " ", raw or ""))
    return re.sub(r"\s+", " ", txt).strip()


def fetch_wiki_urba_venues() -> dict[str, dict]:
    venues: dict[str, dict] = {}
    d = fetch_json(
        "https://es.wikipedia.org/w/api.php?action=parse&page=Uni%C3%B3n_de_Rugby_de_Buenos_Aires&prop=wikitext&format=json",
        timeout=12,
    )
    wt = ((d or {}).get("parse") or {}).get("wikitext", {}).get("*", "") if isinstance(d, dict) else ""
    if not wt:
        return venues
    def clean_cell(c: str) -> str:
        c = re.sub(r"\[\[([^|\]]+\|)?([^\]]+)\]\]", r"\2", c)
        c = re.sub(r"\{\{[^}]*\}\}", "", c)
        c = re.sub(r"<[^>]+>", "", c)
        return re.sub(r"\s+", " ", c).strip(" |.")
    for line in wt.splitlines():
        if line.startswith("||") or (line.startswith("|[[") and "||" in line):
            parts = [clean_cell(p) for p in line.lstrip("|").split("||")]
            if len(parts) >= 4 and parts[0]:
                cname = parts[0]
                sede = parts[2]
                cancha = parts[3]
                nk = norm_key(cname)
                venues[nk] = {"sede": sede, "cancha": cancha, "jurisdiccion": parts[1]}
                # Also index simplified aliases (e.g. without Club / Rugby Club / Asociación)
                short = re.sub(r"\b(club|atletico|asociacion|rugby|hockey|de)\b", " ", nk)
                short = re.sub(r"\s+", " ", short).strip()
                if short and short not in venues:
                    venues[short] = venues[nk]
    return venues


def urba_crest(img_uri: str | None) -> str:
    if not img_uri:
        return ""
    uri = img_uri.replace("ñ", "n").replace("Ñ", "N")
    if uri.startswith("http"):
        return clean_url(uri)
    return "https://urbaimagenes-cddyfadwc8dqcchn.z03.azurefd.net/" + uri.lstrip("/")


def fetch_urba_official_data(conn: sqlite3.Connection) -> tuple[dict, list[dict]]:
    filters = fetch_json("https://urba.org.ar/api/fixture/filters", timeout=12) or {}
    wiki_venues = fetch_wiki_urba_venues()
    clubs_raw = filters.get("clubs") or []
    clubs_map: dict[int, dict] = {}
    for c in clubs_raw:
        cid = int(c.get("id") or 0)
        name = str(c.get("name") or "").strip()
        if not cid or not name:
            continue
        crest = urba_crest(c.get("image_uri_with_base_url") or c.get("image_uri"))
        nk = norm_key(name)
        URBA_ALIASES = {
            "casi": "club atletico de san isidro",
            "sic": "san isidro club",
            "cuba": "club universitario de buenos aires (cuba)",
            "b a c r c": "buenos aires cricket & rugby club",
            "bacrc": "buenos aires cricket & rugby club",
            "geba": "club de gimnasia y esgrima de buenos aires",
            "daom": "daom",
            "sitas": "societa italiana di tiro a segno",
            "atletico del rosario": "club atletico del rosario",
            "regatas bella vista": "club de regatas bella vista",
            "newman": "club newman",
            "hindu": "hindu club",
            "belgrano athletic": "belgrano athletic club",
            "la plata": "la plata rugby club",
            "pucara": "club pucara",
            "los tilos": "club de rugby los tilos",
            "san luis": "club san luis",
            "champagnat": "club champagnat",
            "lomas athletic": "lomas athletic club",
            "san albano": "asociacion ex alumnos del colegio san albano",
            "hurling": "hurling club",
            "pueyrredon": "club pueyrredon",
            "san martin": "club san martin",
            "olivos": "olivos rugby club",
            "curupayti": "curupayti club de rugby",
            "san cirano": "club san cirano",
            "manuel belgrano": "club manuel belgrano",
            "mariano moreno": "asociacion cultural y deportiva mariano moreno",
            "san andres": "club san andres",
            "st brendan's": "st. brendan's",
            "banco nacion": "club banco nacion",
            "universitario la plata": "club universitario de la plata",
            "deportiva francesa": "asociacion deportiva francesa",
            "delta": "delta rugby club",
            "don bosco": "don bosco rugby",
            "argentino": "club argentino de rugby",
            "los matreres": "los matreros",
            "los matreros": "los matreros",
            "monte grande": "monte grande rugby club",
            "san fernando": "club san fernando",
            "san patricio": "club san patricio",
            "casa de padua": "casa de padua",
            "el retiro": "club el retiro",
            "albatros": "club albatros",
            "lanus": "lanus rugby club",
            "liceo militar": "liceo militar",
            "vicente lopez": "municipalidad de vicente lopez",
            "tigre": "tigre rugby club",
            "ciudad de buenos aires": "club ciudad de buenos aires",
            "san carlos": "club san carlos",
            "varela jr": "varela junior",
            "zarate": "arsenal zarate rugby",
        }
        alias_k = URBA_ALIASES.get(nk.replace(".", " ").strip(), "") or URBA_ALIASES.get(nk, "")
        v_info = wiki_venues.get(nk) or (wiki_venues.get(alias_k) if alias_k else None)
        if not v_info:
            short = re.sub(r"\b(club|atletico|asociacion|rugby|hockey|de|c s c y d)\b", " ", nk)
            short = re.sub(r"\s+", " ", short).strip()
            for wk, wv in wiki_venues.items():
                if (alias_k and alias_k in wk) or (short and len(short) >= 4 and (short in wk or wk in short)):
                    v_info = wv
                    break
        clubs_map[cid] = {
            "id": cid,
            "key": nk,
            "name": name,
            "crestUrl": crest,
            "division": "URBA",
            "standingsPos": None,
            "pts": None,
            "played": 0,
            "won": 0,
            "lost": 0,
            "sede": (v_info or {}).get("sede") or "",
            "cancha": (v_info or {}).get("cancha") or "",
        }

    def fetch_champ(cfg):
        return cfg, fetch_json(f"https://urba.org.ar/api/fixture/championship/{cfg['id']}", timeout=14)

    championships_out: list[dict] = []
    urba_feed_matches: list[dict] = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as ex:
        for cfg, data in ex.map(fetch_champ, URBA_CHAMPIONSHIPS):
            if not isinstance(data, dict):
                continue
            champ_obj = data.get("championship") or {}
            standings_raw = data.get("standings") or []
            standings_out: list[dict] = []
            for st in standings_raw:
                tm = st.get("team") or {}
                cl = tm.get("club") or {}
                club_id = int(tm.get("club_id") or cl.get("id") or 0)
                cname = str(cl.get("name") or tm.get("name") or "").strip()
                crest = urba_crest(cl.get("image_uri_with_base_url") or cl.get("image_uri")) or (clubs_map.get(club_id, {}).get("crestUrl") or "")
                row = {
                    "pos": int(st.get("position") or len(standings_out) + 1),
                    "clubId": club_id,
                    "name": cname,
                    "crestUrl": crest,
                    "played": int(st.get("played") or 0),
                    "won": int(st.get("won") or 0),
                    "tied": int(st.get("tied") or 0),
                    "lost": int(st.get("lost") or 0),
                    "pf": int(st.get("points_favor") or 0),
                    "pc": int(st.get("points_against") or 0),
                    "bonus": int(st.get("bonus_offensive") or 0) + int(st.get("bonus_defensive") or 0),
                    "pts": int(st.get("points_total") or 0),
                }
                standings_out.append(row)
                if club_id in clubs_map and cfg["short"] != "Femenino":
                    clubs_map[club_id]["division"] = cfg["short"]
                    clubs_map[club_id]["standingsPos"] = row["pos"]
                    clubs_map[club_id]["pts"] = row["pts"]
                    clubs_map[club_id]["played"] = row["played"]
                    clubs_map[club_id]["won"] = row["won"]
                    clubs_map[club_id]["lost"] = row["lost"]
                    if not clubs_map[club_id]["crestUrl"] and crest:
                        clubs_map[club_id]["crestUrl"] = crest

            rounds = champ_obj.get("rounds") or []
            recent_rounds = rounds[-3:] if len(rounds) > 3 else rounds
            champ_recent_matches: list[dict] = []
            for rd in reversed(recent_rounds):
                rname = str(rd.get("name") or "").strip()
                pdate = parse_iso_dt(rd.get("playdate"))
                times = format_arg_times(pdate)
                for m in rd.get("matches") or []:
                    lt = m.get("local_team") or {}
                    vt = m.get("visit_team") or {}
                    lc = lt.get("club") or {}
                    vc = vt.get("club") or {}
                    lid = int(lt.get("club_id") or lc.get("id") or 0)
                    vid = int(vt.get("club_id") or vc.get("id") or 0)
                    h_name = str(lt.get("name") or lc.get("name") or "Local").strip()
                    a_name = str(vt.get("name") or vc.get("name") or "Visitante").strip()
                    h_crest = urba_crest(lc.get("image_uri_with_base_url") or lc.get("image_uri")) or (clubs_map.get(lid, {}).get("crestUrl") or "")
                    a_crest = urba_crest(vc.get("image_uri_with_base_url") or vc.get("image_uri")) or (clubs_map.get(vid, {}).get("crestUrl") or "")
                    fulfilled = bool(m.get("fulfilled"))
                    suspended = bool(m.get("suspended"))
                    status = "finished" if fulfilled else "scheduled"
                    h_sc = m.get("local_team_score") if fulfilled else None
                    a_sc = m.get("visit_team_score") if fulfilled else None
                    cancha = (clubs_map.get(lid) or {}).get("cancha") or (clubs_map.get(lid) or {}).get("sede") or "Buenos Aires"
                    m_obj = {
                        "id": f"urba:{cfg['id']}:{m.get('id')}",
                        "source": "urba",
                        "eventId": str(m.get("id") or ""),
                        "leagueId": str(cfg["id"]),
                        "leagueName": cfg["name"],
                        "leagueShort": f"URBA {cfg['short']}",
                        "phase": rname,
                        "group": "urba",
                        "compId": "ar-urba-top14",
                        "category": "Clubes",
                        "status": status,
                        "statusText": "Suspendido" if suspended else ("Finalizado" if fulfilled else "Programado"),
                        "isArgentina": True,
                        "venue": {"name": cancha, "city": "Buenos Aires", "country": "Argentina"},
                        "odds": None,
                        **times,
                        "home": {"name": h_name, "abbr": h_name[:3].upper(), "crestUrl": h_crest, "score": h_sc, "lineup": [], "coach": ""},
                        "away": {"name": a_name, "abbr": a_name[:3].upper(), "crestUrl": a_crest, "score": a_sc, "lineup": [], "coach": ""},
                        "scoring": [],
                        "cards": [],
                        "officials": [],
                    }
                    champ_recent_matches.append(m_obj)
                    if rd == recent_rounds[-1] or (cfg["short"] == "Top 14" and rname.upper() == "PLAYOFF"):
                        urba_feed_matches.append(m_obj)

            championships_out.append({
                "id": cfg["id"],
                "name": cfg["name"],
                "short": cfg["short"],
                "standings": standings_out,
                "recentMatches": champ_recent_matches[:16],
                "totalRounds": len(rounds),
            })

    now_iso = dt.datetime.now(UTC_TZ).strftime("%Y-%m-%dT%H:%M:%SZ")
    clubs_list = sorted(clubs_map.values(), key=lambda c: (
        {"Top 14": 0, "Primera A": 1, "Primera B": 2, "Primera C": 3, "Segunda": 4, "Tercera": 5, "Desarrollo": 6}.get(c["division"], 9),
        c["standingsPos"] or 99,
        c["name"].lower(),
    ))
    for c in clubs_list:
        nk = c["key"]
        existing = conn.execute("SELECT coach, stadium, wiki_title FROM team_meta WHERE team_key=?", (nk,)).fetchone()
        stad = c["cancha"] or c["sede"] or (existing[1] if existing else "")
        conn.execute(
            """INSERT INTO team_meta(team_key, display_name, espn_id, crest_url, coach, stadium, country, wiki_title, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT(team_key) DO UPDATE SET
                 crest_url=CASE WHEN excluded.crest_url != '' THEN excluded.crest_url ELSE team_meta.crest_url END,
                 stadium=CASE WHEN excluded.stadium != '' THEN excluded.stadium ELSE team_meta.stadium END,
                 country='Argentina',
                 updated_at=excluded.updated_at""",
            (nk, c["name"], "", c["crestUrl"], existing[0] if existing else "", stad, "Argentina", existing[2] if existing else "", now_iso),
        )
    conn.commit()
    return {"championships": championships_out, "clubs": clubs_list, "totalClubs": len(clubs_list)}, urba_feed_matches


def _has_kw(text: str, words: tuple[str, ...]) -> bool:
    pat = r"(?:^|\b)(?:" + "|".join(re.escape(w) for w in words) + r")(?:\b|$)"
    return bool(re.search(pat, text))


def classify_news_topic(title: str, summary: str, source: str, default_cat: str) -> tuple[str, str, list[str]]:
    text = norm_key(f"{title} {summary}")
    tags: list[str] = []

    lineup_kw = (
        "alineacion", "alineaciones", "formacion", "formaciones", "plantel", "convocados",
        "convocatoria", "xv inicial", "baja", "bajas", "lesion", "lesiones", "lesionado",
        "desgarro", "operado", "vuelve", "regresa", "titular", "titulares", "suplente",
        "suplentes", "equipo confirmado", "capitan", "lineup", "line-up", "team news",
        "starting xv", "squad", "squads", "recalled", "injury", "injured", "injuries",
        "ruled out", "fit for", "dropped", "selection", "selected", "naming", "compo",
        "compos", "groupe", "blesses", "blesse", "retour", "changes", "rotacion", "debutante"
    )
    transfer_kw = (
        "fichaje", "fichajes", "ficha", "refuerzo", "refuerzos", "refuerza", "renueva",
        "renovacion", "contrato", "contratos", "mercado", "se suma", "deja el club",
        "dejara", "jugara en", "despedida", "transfer", "transfers", "transfert",
        "transferts", "mercato", "signs", "signed", "signing", "signings", "contract",
        "extension", "prolongation", "joins", "re-signs", "move to", "departure",
        "exit", "retire", "retirement", "retiro"
    )
    pumas_kw = (
        "los pumas", "pumas", "pumas 7s", "uar", "contepomi", "gomez cora", "argentina",
        "pampas", "dogos xv", "dogos", "tarucas", "matera", "carreras", "chocobares",
        "albornoz", "kremer", "montoya", "mallia", "moroni", "aleman", "delguy", "moneta",
        "miotti"
    )
    urba_kw = (
        "urba", "buenos aires", "newman", "hindu", "sic", "casi", "belgrano", "alumni",
        "cuba", "la plata", "pucara", "los tilos", "regatas", "champagnat", "san luis",
        "top 14 de la urba", "seven oficial de la urba"
    )

    is_lineup = _has_kw(text, lineup_kw)
    is_transfer = _has_kw(text, transfer_kw)
    is_pumas = default_cat == "pumas" or _has_kw(text, pumas_kw)
    is_urba = default_cat == "urba" or _has_kw(text, urba_kw)

    if is_lineup:
        tags.append("alineaciones")
    if is_transfer:
        tags.append("fichajes")
    if is_pumas:
        tags.append("pumas")
    if is_urba:
        tags.append("urba")
    if not tags or default_cat == "internacional":
        if "internacional" not in tags:
            tags.append("internacional")

    if is_transfer:
        return "fichajes", "Fichajes & Mercado", tags
    if is_lineup:
        return "alineaciones", "Alineaciones & Bajas", tags
    if is_urba:
        return "urba", "URBA Bs.As.", tags
    if is_pumas:
        return "pumas", "Los Pumas / UAR", tags
    return "internacional", "Mundo Rugby", tags

def normalize_news_img(url: str | None) -> str:
    u = clean_url(url)
    if not u:
        return ""
    if u.startswith("http://"):
        u = "https://" + u[len("http://"):]
    # Upgrade Planet Rugby / WP square thumbnails like -120x120.jpg to full image
    u = re.sub(r"-\d{2,4}x\d{2,4}(?=\.(?:jpg|jpeg|png|webp)(?:\?|$))", "", u, flags=re.IGNORECASE)
    return u


def normalize_news_link(url: str | None) -> str:
    u = str(url or "").strip()
    if u.startswith("http://www.worldrugby.org"):
        u = "https://www.world.rugby" + u[len("http://www.worldrugby.org"):]
    elif u.startswith("http://worldrugby.org"):
        u = "https://www.world.rugby" + u[len("http://worldrugby.org"):]
    return u


def fetch_rugby_news() -> list[dict]:
    items: list[dict] = []

    def build_item(nid: str, title: str, summary: str, link: str, img: str, source: str, default_cat: str, pdt: dt.datetime | None) -> dict | None:
        title_c = strip_html(title)
        link_c = normalize_news_link(link)
        if not title_c or not link_c:
            return None
        sum_c = strip_html(summary)
        if len(sum_c) > 210:
            sum_c = sum_c[:207].rstrip() + "..."
        times = format_arg_times(pdt)
        topic, topic_label, tags = classify_news_topic(title_c, sum_c, source, default_cat)
        return {
            "id": nid,
            "title": title_c,
            "summary": sum_c,
            "url": link_c,
            "imageUrl": normalize_news_img(img),
            "source": source,
            "category": default_cat if default_cat in ("pumas", "urba") else topic,
            "topic": topic,
            "topicLabel": topic_label,
            "tags": tags,
            "dateArg": times["dateArg"],
            "ts": times["ts"],
        }

    def from_wp(url: str, source: str, default_cat: str):
        posts = fetch_json(url, timeout=14)
        if not isinstance(posts, list):
            return []
        out = []
        for p in posts:
            media = (p.get("_embedded") or {}).get("wp:featuredmedia") or []
            img = ""
            if media and isinstance(media[0], dict):
                img = media[0].get("source_url") or ""
            pdt = parse_iso_dt(p.get("date_gmt") or p.get("date"))
            it = build_item(
                f"{source}:{p.get('id')}",
                (p.get("title") or {}).get("rendered") or "",
                (p.get("excerpt") or {}).get("rendered") or "",
                p.get("link") or "",
                img,
                source,
                default_cat,
                pdt,
            )
            if it:
                out.append(it)
        return out

    def from_world_rugby_es():
        data = fetch_json("https://api.wr-rims-prod.pulselive.com/content/worldrugby/text/es/?pageSize=18", timeout=14)
        if not isinstance(data, dict):
            return []
        out = []
        for c in data.get("content") or []:
            lm = c.get("leadMedia") or {}
            img = c.get("imageUrl") or lm.get("imageUrl") or lm.get("url") or ""
            pdt = parse_iso_dt(c.get("date") or c.get("publishFrom"))
            it = build_item(
                f"WorldRugbyES:{c.get('id')}",
                c.get("title") or "",
                c.get("summary") or c.get("description") or c.get("subtitle") or "",
                c.get("canonicalUrl") or f"https://www.world.rugby/news/{c.get('id')}",
                img,
                "World Rugby ES",
                "internacional",
                pdt,
            )
            if it:
                out.append(it)
        return out

    def from_rss(url: str, source: str, default_cat: str, limit: int = 16):
        raw = fetch_text(url, timeout=14)
        if not raw or "<rss" not in raw:
            return []
        out = []
        try:
            root = ET.fromstring(raw)
            for idx, it_el in enumerate(root.findall(".//item")[:limit]):
                pub = str(it_el.findtext("pubDate") or "").strip()
                pdt = None
                if pub:
                    try:
                        from email.utils import parsedate_to_datetime
                        pdt = parsedate_to_datetime(pub).astimezone(UTC_TZ)
                    except Exception:
                        pdt = None
                # Choose largest resolution media:content / thumbnail / enclosure
                best_img = ""
                best_w = -1
                for child in list(it_el):
                    tag = child.tag.split("}")[-1].lower()
                    u = child.attrib.get("url") or ""
                    if tag in ("thumbnail", "content", "enclosure") and u:
                        w_raw = str(child.attrib.get("width") or "0")
                        w = int(w_raw) if w_raw.isdigit() else (600 if tag == "enclosure" else 200)
                        if w >= best_w:
                            best_w = w
                            best_img = u
                ts_hint = int(pdt.timestamp()) if pdt else idx
                item = build_item(
                    f"{source}:{idx}:{ts_hint}",
                    it_el.findtext("title") or "",
                    it_el.findtext("description") or "",
                    it_el.findtext("link") or "",
                    best_img,
                    source,
                    default_cat,
                    pdt,
                )
                if item:
                    out.append(item)
        except Exception:
            pass
        return out

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as ex:
        futs = [
            ex.submit(from_wp, "https://uar.com.ar/wp-json/wp/v2/posts?per_page=12&_embed", "UAR Oficial", "pumas"),
            ex.submit(from_wp, "https://urba.org.ar/wp-json/wp/v2/posts?per_page=12&_embed", "URBA Oficial", "urba"),
            ex.submit(from_world_rugby_es),
            ex.submit(from_rss, "https://www.cordobaxv.com.ar/feed/", "Córdoba XV", "pumas", 12),
            ex.submit(from_rss, "https://www.planetrugby.com/rss", "Planet Rugby", "internacional", 16),
            ex.submit(from_rss, "https://www.rugbyrama.fr/rss.xml", "Rugbyrama", "internacional", 18),
            ex.submit(from_rss, "https://www.skysports.com/rss/12321", "Sky Sports Rugby", "internacional", 16),
            ex.submit(from_rss, "https://www.theguardian.com/sport/rugby-union/rss", "The Guardian", "internacional", 14),
            ex.submit(from_rss, "https://feeds.bbci.co.uk/sport/rugby-union/rss.xml", "BBC Rugby", "internacional", 14),
        ]
        for f in concurrent.futures.as_completed(futs):
            try:
                items.extend(f.result())
            except Exception:
                pass

    seen = set()
    deduped = []
    for it in sorted(items, key=lambda x: x["ts"], reverse=True):
        k = norm_key(it["title"])[:64]
        if k not in seen:
            seen.add(k)
            deduped.append(it)
    return deduped[:90]



def fetch_sra_official_data(conn: sqlite3.Connection) -> tuple[list[dict], list[dict]]:
    now_iso = dt.datetime.now(UTC_TZ).strftime("%Y-%m-%dT%H:%M:%SZ")
    franchise_map = {norm_key(fr["name"]): fr for fr in SRA_FRANCHISES}
    franchise_map.update({fr["key"]: fr for fr in SRA_FRANCHISES})

    for fr in SRA_FRANCHISES:
        conn.execute(
            """INSERT INTO team_meta(team_key, display_name, espn_id, crest_url, coach, stadium, country, wiki_title, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT(team_key) DO UPDATE SET
                 crest_url=CASE WHEN excluded.crest_url != '' THEN excluded.crest_url ELSE team_meta.crest_url END,
                 coach=CASE WHEN excluded.coach != '' THEN excluded.coach ELSE team_meta.coach END,
                 stadium=CASE WHEN excluded.stadium != '' THEN excluded.stadium ELSE team_meta.stadium END,
                 country=excluded.country,
                 updated_at=excluded.updated_at""",
            (fr["key"], fr["name"], "", fr["crestUrl"], fr["coach"], fr["stadium"], fr["country"], "", now_iso),
        )
    conn.commit()

    standings_out: list[dict] = []
    dash_html = fetch_text("https://regisar.sudamerica.rugby/embed/dashboard", timeout=12)
    if dash_html:
        pos_m = re.search(r'id="positions-card".*?<tbody[^>]*>(.*?)</tbody>', dash_html, re.S)
        if pos_m:
            for row_html in re.findall(r"<tr[^>]*>(.*?)</tr>", pos_m.group(1), re.S):
                tds = re.findall(r"<td[^>]*>(.*?)</td>", row_html, re.S)
                if len(tds) < 13:
                    continue
                pos_txt = strip_html(tds[0])
                raw_team_txt = strip_html(tds[1])
                clean_name = raw_team_txt
                for fr in SRA_FRANCHISES:
                    if fr["name"].lower() in raw_team_txt.lower():
                        clean_name = fr["name"]
                        break
                nk = norm_key(clean_name)
                fr_info = franchise_map.get(nk, {})
                try:
                    standings_out.append({
                        "pos": int(re.sub(r"[^0-9]", "", pos_txt) or (len(standings_out) + 1)),
                        "name": clean_name,
                        "crestUrl": fr_info.get("crestUrl", ""),
                        "flagUrl": get_country_flag_url(nk),
                        "played": int(strip_html(tds[2]) or 0),
                        "won": int(strip_html(tds[3]) or 0),
                        "tied": int(strip_html(tds[4]) or 0),
                        "lost": int(strip_html(tds[5]) or 0),
                        "pf": int(strip_html(tds[6]) or 0),
                        "pc": int(strip_html(tds[7]) or 0),
                        "diff": strip_html(tds[10]),
                        "bonus": int(strip_html(tds[11]) or 0),
                        "pts": int(strip_html(tds[12]) or 0),
                    })
                except Exception:
                    continue

    sra_matches: list[dict] = []
    seen_sra_ids = set()
    for page_num in (4, 3):
        p_html = fetch_text(f"https://regisar.sudamerica.rugby/embed/partidos?page={page_num}", timeout=12)
        if not p_html:
            continue
        tb = re.search(r"<tbody[^>]*>(.*?)</tbody>", p_html, re.S)
        if not tb:
            continue
        for r_html in re.findall(r"<tr[^>]*>(.*?)</tr>", tb.group(1), re.S):
            tds = re.findall(r"<td[^>]*>(.*?)</td>", r_html, re.S)
            if len(tds) < 6:
                continue
            num_id = re.sub(r"[^0-9]", "", strip_html(tds[0]))
            if not num_id or num_id in seen_sra_ids:
                continue
            seen_sra_ids.add(num_id)

            date_parts = strip_html(tds[1])
            dm = re.search(r"(\d{2})/(\d{2})/(\d{4}).*?(\d{2}):(\d{2})", date_parts)
            utc_dt = None
            if dm:
                dd, mm, yyyy, hh, mi = map(int, dm.groups())
                local_dt = dt.datetime(yyyy, mm, dd, hh, mi, tzinfo=ARG_TZ)
                utc_dt = local_dt.astimezone(UTC_TZ)
            times = format_arg_times(utc_dt)

            round_name = strip_html(tds[2]) or "Súper Rugby Américas"
            score_cell = tds[3]
            teams_found = re.findall(r'<div class="text-white font-medium text-sm">\s*(.*?)\s*</div>', score_cell, re.S)
            if len(teams_found) < 2:
                continue
            h_name = strip_html(teams_found[0])
            a_name = strip_html(teams_found[1])

            sc_m = re.search(r"(\d+)\s*-\s*(\d+)", score_cell)
            h_sc = sc_m.group(1) if sc_m else None
            a_sc = sc_m.group(2) if sc_m else None
            venue_txt = strip_html(tds[4])
            status_raw = strip_html(tds[5]).lower()
            status = "finished" if ("final" in status_raw or h_sc is not None) else ("live" if "curso" in status_raw else "scheduled")

            h_nk = norm_key(h_name)
            a_nk = norm_key(a_name)
            h_fr = franchise_map.get(h_nk, {})
            a_fr = franchise_map.get(a_nk, {})
            is_arg = (h_fr.get("flag") == "ar" or a_fr.get("flag") == "ar")

            sra_matches.append({
                "id": f"sra:{num_id}",
                "source": "sudamerica-rugby",
                "eventId": num_id,
                "leagueId": "regisar-11",
                "leagueName": "Súper Rugby Américas",
                "leagueShort": "Súper Rugby Américas",
                "phase": round_name,
                "group": "sra",
                "compId": "sa-super-rugby-americas",
                "category": "Clubes",
                "status": status,
                "statusText": "Finalizado" if status == "finished" else ("En Curso" if status == "live" else "Programado"),
                "isArgentina": is_arg,
                "venue": {"name": venue_txt or h_fr.get("stadium", ""), "city": h_fr.get("city", ""), "country": h_fr.get("country", "")},
                "odds": None,
                **times,
                "home": {
                    "name": h_name,
                    "abbr": h_fr.get("abbr") or h_name[:3].upper(),
                    "crestUrl": h_fr.get("crestUrl", ""),
                    "flagUrl": get_country_flag_url(h_nk),
                    "score": h_sc if status != "scheduled" else None,
                    "lineup": [],
                    "coach": h_fr.get("coach", ""),
                },
                "away": {
                    "name": a_name,
                    "abbr": a_fr.get("abbr") or a_name[:3].upper(),
                    "crestUrl": a_fr.get("crestUrl", ""),
                    "flagUrl": get_country_flag_url(a_nk),
                    "score": a_sc if status != "scheduled" else None,
                    "lineup": [],
                    "coach": a_fr.get("coach", ""),
                },
                "scoring": [],
                "cards": [],
                "officials": [],
            })

    sra_matches.sort(key=lambda x: -(x.get("ts") or 0))
    return standings_out, sra_matches[:24]


def fetch_espn_standings_for_comp(comp: dict, meta_map: dict) -> list[dict]:
    lid = comp.get("standingsLeagueId")
    if not lid:
        return []
    season_q = f"?season={comp['seasonParam']}" if comp.get("seasonParam") else ""
    d = fetch_json(f"https://site.api.espn.com/apis/v2/sports/rugby/{lid}/standings{season_q}", timeout=10)
    if not isinstance(d, dict):
        return []
    groups = d.get("children") or [d]
    tables = []
    for g in groups:
        st = g.get("standings") or {}
        entries = st.get("entries") or []
        if not entries:
            continue
        rows = []
        for idx, e in enumerate(entries, 1):
            tm = e.get("team") or {}
            tname = str(tm.get("displayName") or tm.get("name") or "").strip()
            if not tname:
                continue
            tnk = norm_key(tname)
            stats_map = {str(s.get("name") or "").lower(): s for s in (e.get("stats") or [])}
            def sval(k, default="0"):
                s = stats_map.get(k.lower())
                if not s:
                    return default
                return s.get("displayValue") if s.get("displayValue") is not None else str(s.get("value") or default)
            gp = int(float(sval("gamesPlayed", "0") or 0))
            if lid == "242041" and "rebels" in tnk and gp == 0:
                continue
            logos = tm.get("logos") or []
            raw_logo = clean_url(logos[0].get("href")) if logos else ""
            crest = resolve_crest_url(tnk, raw_logo, meta_map)
            rows.append({
                "pos": int(float(sval("rank", str(idx)) or idx)),
                "name": tname,
                "crestUrl": crest,
                "flagUrl": get_country_flag_url(tnk),
                "played": gp,
                "won": int(float(sval("gamesWon", "0") or 0)),
                "tied": int(float(sval("gamesDrawn", "0") or 0)),
                "lost": int(float(sval("gamesLost", "0") or 0)),
                "pf": int(float(sval("pointsFor", "0") or 0)),
                "pc": int(float(sval("pointsAgainst", "0") or 0)),
                "diff": str(sval("pointsDifference", "0")),
                "bonus": int(float(sval("bonusPoints", "0") or 0)),
                "pts": int(float(sval("points", "0") or 0)),
            })
        if rows:
            rows.sort(key=lambda r: (r["pos"], -r["pts"]))
            tables.append({
                "groupName": g.get("name") or comp["shortName"],
                "seasonLabel": str(st.get("seasonDisplayName") or st.get("season") or comp["season"]),
                "rows": rows,
            })
    return tables


def build_competitions_catalog(
    conn: sqlite3.Connection,
    matches: list[dict],
    sra_standings: list[dict],
    urba_data: dict,
    meta_map: dict,
) -> tuple[list[dict], list[dict]]:
    now_iso = dt.datetime.now(UTC_TZ).strftime("%Y-%m-%dT%H:%M:%SZ")
    standings_by_id: dict[str, list[dict]] = {}

    def _fetch(comp):
        return comp["id"], fetch_espn_standings_for_comp(comp, meta_map)

    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as ex:
        for cid, tbls in ex.map(_fetch, [c for c in COMPETITIONS_CATALOG if c.get("standingsLeagueId")]):
            standings_by_id[cid] = tbls

    urba_champs = (urba_data or {}).get("championships") or []
    if urba_champs and urba_champs[0].get("standings"):
        standings_by_id["ar-urba-top14"] = [{
            "groupName": urba_champs[0].get("name") or "URBA Top 14",
            "seasonLabel": "2026",
            "rows": urba_champs[0]["standings"],
        }]
    if sra_standings:
        standings_by_id["sa-super-rugby-americas"] = [{
            "groupName": "Tabla General — Súper Rugby Américas 2026",
            "seasonLabel": "2026",
            "rows": sra_standings,
        }]

    out_comps: list[dict] = []
    for c in COMPETITIONS_CATALOG:
        cid = c["id"]
        if cid == "ar-urba-top14":
            m_count = sum(1 for m in matches if m.get("group") == "urba")
        elif cid == "ar-los-pumas":
            m_count = sum(1 for m in matches if m.get("isArgentina") and m.get("group") != "urba")
        else:
            m_count = sum(1 for m in matches if m.get("compId") == cid)

        conn.execute(
            """INSERT INTO competitions(id, name, short_name, region, category, group_key, source, external_id, teams_count, teams_note, season_window, is_argentina_core, sort_order, matches_count, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT(id) DO UPDATE SET
                 name=excluded.name,
                 short_name=excluded.short_name,
                 region=excluded.region,
                 category=excluded.category,
                 group_key=excluded.group_key,
                 source=excluded.source,
                 external_id=excluded.external_id,
                 teams_count=excluded.teams_count,
                 teams_note=excluded.teams_note,
                 season_window=excluded.season_window,
                 is_argentina_core=excluded.is_argentina_core,
                 sort_order=excluded.sort_order,
                 matches_count=excluded.matches_count,
                 updated_at=excluded.updated_at""",
            (
                cid, c["name"], c["shortName"], c["region"], c["category"], c["groupKey"],
                c["source"], c.get("externalId") or "", c["teamsCount"], c.get("teamsNote") or "",
                c["season"], 1 if c["isArgentinaCore"] else 0, c["sortOrder"], m_count, now_iso,
            ),
        )
        entry = {
            "id": cid,
            "name": c["name"],
            "shortName": c["shortName"],
            "region": c["region"],
            "category": c["category"],
            "groupKey": c["groupKey"],
            "source": c["source"],
            "externalId": c.get("externalId") or "",
            "teamsCount": c["teamsCount"],
            "teamsNote": c.get("teamsNote") or "",
            "season": c["season"],
            "isArgentinaCore": bool(c["isArgentinaCore"]),
            "sortOrder": c["sortOrder"],
            "matchesCount": m_count,
            "notes": c.get("notes") or "",
            "standings": standings_by_id.get(cid, []),
        }
        if cid == "sa-super-rugby-americas":
            entry["franchises"] = SRA_FRANCHISES
        out_comps.append(entry)

    conn.commit()
    cat_order = ["Clubes", "Copas", "Selecciones"]
    cat_desc = {
        "Clubes": "Ligas principales de clubes y franquicias (Top 14 Francia diferenciado de URBA Bs.As., URC, Premiership, Super Rugby Pacific y Súper Rugby Américas).",
        "Copas": "Copas continentales de clubes de Europa e invitados organizadas por EPCR.",
        "Selecciones": "Torneos internacionales de selecciones nacionales (Los Pumas intactos, The Rugby Championship, Seis Naciones y Mundial RWC).",
    }
    categories = []
    for cat in cat_order:
        comps_cat = [x for x in out_comps if x["category"] == cat]
        categories.append({
            "id": cat.lower(),
            "name": cat,
            "description": cat_desc[cat],
            "competitionsCount": len(comps_cat),
            "internationalCount": sum(1 for x in comps_cat if not x["isArgentinaCore"]),
            "argentinaCoreCount": sum(1 for x in comps_cat if x["isArgentinaCore"]),
            "competitionIds": [x["id"] for x in comps_cat],
        })
    return out_comps, categories


def build_confirmed_lineups(matches: list[dict], teams_dir: list[dict]) -> list[dict]:
    dir_map = {t["key"]: t for t in teams_dir}
    out = []
    seen_teams = set()
    # 1) Prioritize matches with actual verified lineups, Argentina first then newest
    cand_matches = [
        m for m in matches
        if (m.get("home", {}).get("lineup") or m.get("away", {}).get("lineup"))
    ]
    cand_matches.sort(key=lambda m: (0 if m.get("isArgentina") else 1, -(m.get("ts") or 0)))

    for m in cand_matches:
        for side_key, opp_key in (("home", "away"), ("away", "home")):
            tm = m[side_key]
            lineup = tm.get("lineup") or []
            if len(lineup) < 7:
                continue
            tnk = norm_key(tm.get("name") or "")
            if not tnk or tnk in seen_teams:
                continue
            seen_teams.add(tnk)
            d_entry = dir_map.get(tnk, {})
            captain_p = next((p.get("name") for p in lineup if p.get("captain")), "")
            out.append({
                "teamKey": tnk,
                "teamName": tm.get("name") or "",
                "opponentName": (m[opp_key] or {}).get("name") or "",
                "leagueName": m.get("leagueShort") or m.get("leagueName") or "",
                "matchId": m.get("id") or "",
                "dateArg": m.get("dateArg") or "",
                "ts": m.get("ts") or 0,
                "crestUrl": tm.get("crestUrl") or d_entry.get("crestUrl") or "",
                "flagUrl": tm.get("flagUrl") or d_entry.get("flagUrl") or "",
                "coach": tm.get("coach") or d_entry.get("coach") or "",
                "captain": captain_p or "",
                "startersCount": sum(1 for p in lineup if p.get("starter")),
                "playersCount": len(lineup),
                "players": lineup,
                "isArgentina": bool(m.get("isArgentina") or "argentina" in tnk),
            })
            if len(out) >= 18:
                break
        if len(out) >= 18:
            break
    out.sort(key=lambda x: (0 if x["isArgentina"] else 1, -(x["ts"] or 0)))
    return out

def main() -> None:
    now_utc = dt.datetime.now(UTC_TZ)
    conn = init_db()
    team_index = harvest_espn_teams(conn)
    urba_data, urba_feed_matches = fetch_urba_official_data(conn)
    sra_standings, sra_matches = fetch_sra_official_data(conn)
    news_items = fetch_rugby_news()
    espn_matches = parse_espn_matches(now_utc)
    wr_matches = parse_world_rugby_matches(now_utc)
    matches = merge_and_dedupe(espn_matches, wr_matches)
    enrich_match_details(matches, conn)
    # Append latest official URBA & SRA matches without duplicating ESPN
    existing_keys = {(norm_key(m["home"]["name"]), norm_key(m["away"]["name"]), m["dayArg"]) for m in matches}
    for extra_m in (urba_feed_matches + sra_matches):
        uk = (norm_key(extra_m["home"]["name"]), norm_key(extra_m["away"]["name"]), extra_m["dayArg"])
        if uk not in existing_keys:
            existing_keys.add(uk)
            matches.append(extra_m)

    names_to_enrich = list(WIKI_PAGE_MAP.keys())
    for m in matches:
        names_to_enrich.append(m["home"]["name"])
        names_to_enrich.append(m["away"]["name"])
    enrich_team_meta_from_wiki(conn, names_to_enrich, team_index)

    rankings = fetch_rankings()
    meta_rows_map = {r[0]: {"crestUrl": r[1]} for r in conn.execute("SELECT team_key, crest_url FROM team_meta").fetchall()}
    for cat in ("men", "women"):
        for r in rankings.get(cat, []):
            tnk = norm_key(r["team"])
            r["crestUrl"] = resolve_crest_url(tnk, meta_rows_map.get(tnk, {}).get("crestUrl", ""), meta_rows_map)
            r["flagUrl"] = get_country_flag_url(tnk)

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

    pumas_xv_roster = []
    for m in matches:
        if m.get("group") != "sevens" and m.get("status") == "finished":
            for side in ("home", "away"):
                if norm_key(m[side]["name"]) == "argentina" and m[side].get("lineup"):
                    pumas_xv_roster = m[side]["lineup"]
                    break
            if pumas_xv_roster:
                break
    if not pumas_xv_roster:
        arg_dir = next((t for t in teams_dir if t["key"] == "argentina"), None)
        if arg_dir:
            pumas_xv_roster = arg_dir.get("roster") or []

    arg_tournaments = [
        {"name": "Nations Championship 2026", "team": "Los Pumas XV", "status": "Final 7° puesto vs Escocia · Dom 29 Nov 10:10 ART", "coach": "Felipe Contepomi"},
        {"name": "HSBC SVNS 2027 — Dubai", "team": "Los Pumas 7s", "status": "Pool A vs Sudáfrica, USA y Australia · 28 Nov", "coach": "Santiago Gómez Cora"},
        {"name": "URBA Top 14 2026", "team": "Clubes Buenos Aires", "status": "Bicampeón: Newman (venció a Hindú en la Final)", "coach": "99 clubes afiliados"},
        {"name": "Nacional de Clubes 2026", "team": "Campeones del Interior & URBA", "status": "Campeón 2026: Tala RC", "coach": "UAR Oficial"},
        {"name": "Súper Rugby Américas", "team": "Pampas · Dogos XV · Tarucas", "status": "Franquicias profesionales argentinas", "coach": "Sudamérica Rugby"},
    ]

    confirmed_lineups = build_confirmed_lineups(matches, teams_dir)
    competitions_list, categories_list = build_competitions_catalog(conn, matches, sra_standings, urba_data, meta_rows_map)

    payload = {
        "engineVersion": "3.2.0-RUGBY-GLOBAL-PRO",
        "categories": categories_list,
        "competitions": competitions_list,
        "competitionsSchemaSQL": COMPETITIONS_SCHEMA_SQL,
        "sra": {"standings": sra_standings, "franchises": SRA_FRANCHISES, "matchesCount": len(sra_matches)},
        "confirmedLineups": confirmed_lineups,
        "urba": urba_data,
        "news": news_items,
        "argentinaHub": {
            "xvRoster": pumas_xv_roster,
            "tournaments": arg_tournaments,
            "coaches": {"xv": "Felipe Contepomi", "sevens": "Santiago Gómez Cora"},
        },
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

    preserve_enriched_media_and_kits(payload, OUT_DATA_JSON)
    for path in (OUT_DATA_JSON, OUT_ROOT_JSON):
        with open(path, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False)

    with open(OUT_HISTORY_JSON, "w", encoding="utf-8") as f:
        json.dump({"updatedAt": payload["generatedAt"], "history": history_list, "teams": teams_dir[:80]}, f, ensure_ascii=False)

    with open(OUT_COMPETITIONS_JSON, "w", encoding="utf-8") as f:
        json.dump({
            "updatedAt": payload["generatedAt"],
            "schemaSQL": COMPETITIONS_SCHEMA_SQL,
            "categories": categories_list,
            "competitions": competitions_list,
        }, f, ensure_ascii=False, indent=2)

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
                "newsCount": len(news_items),
                "confirmedLineups": len(confirmed_lineups),
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
