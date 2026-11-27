(() => {
  'use strict';

  const DATA_URL = './data/rugby_engine_output.json';
  const FALLBACK_URL = './rugby_engine_output.json';

  const state = {
    data: null,
    tab: 'matches',
    matchGroup: 'all',
    matchCompId: 'all',
    compViewMode: 'matches',
    matchStatus: 'all',
    search: '',
    urbaChampId: 2025176,
    urbaSubTab: 'standings',
    urbaSearch: '',
    newsCat: 'all',
    newsSearch: '',
    sheetMatchId: null,
    sheetTab: 'summary',
    teamsSubTab: 'all',
    teamsSearch: '',
    rankGender: 'men',
    daylight: localStorage.getItem('rugbyProTheme') === 'daylight',
    deferredInstall: null,
  };

  if (state.daylight) {
    document.documentElement.classList.add('daylight');
  }

  const els = {
    main: document.getElementById('appMain'),
    installBtn: document.getElementById('installBtn'),
    refreshBtn: document.getElementById('refreshBtn'),
    offlineBar: document.getElementById('offlineBar'),
    detailSheet: document.getElementById('detailSheet'),
    detailPanel: document.getElementById('detailPanel'),
    navItems: Array.from(document.querySelectorAll('[data-tab]')),
  };

  function esc(str) {
    return String(str == null ? '' : str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function norm(str) {
    return String(str || '')
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .trim();
  }

  function initials(name) {
    const parts = String(name || 'RB').trim().split(/\s+/).filter(Boolean);
    if (parts.length === 1) return parts[0].slice(0, 3).toUpperCase();
    return (parts[0][0] + (parts[1][0] || '') + (parts[2] ? parts[2][0] : '')).toUpperCase();
  }

  function renderTeamVisual(crestUrl, flagUrl, name, sizeClass = '') {
    const safeUrl = crestUrl && !String(crestUrl).includes('25912.png') ? esc(crestUrl) : '';
    const safeFlag = flagUrl ? esc(flagUrl) : '';
    const ini = esc(initials(name));

    const crestInner = safeUrl
      ? `<img src="${safeUrl}" alt="${esc(name)}" loading="lazy" referrerpolicy="no-referrer"
              onerror="this.style.display='none';this.nextElementSibling.style.display='inline-block';" />
         <span class="crest-fallback" style="display:none;">${ini}</span>`
      : `<span class="crest-fallback">${ini}</span>`;

    const flagCorner = safeFlag && safeFlag !== safeUrl
      ? `<img class="flag-badge" src="${safeFlag}" alt="Bandera ${esc(name)}" loading="lazy" referrerpolicy="no-referrer"
              onerror="this.style.display='none';" />`
      : '';

    return `
      <div class="visual-lockup">
        <div class="crest-frame ${sizeClass}">${crestInner}</div>
        ${flagCorner}
      </div>`;
  }

  function countdownText(tsSec) {
    if (!tsSec) return '';
    const diff = tsSec * 1000 - Date.now();
    if (diff <= 0) return 'Próximo a iniciar';
    const totalMins = Math.floor(diff / 60000);
    const days = Math.floor(totalMins / 1440);
    const hours = Math.floor((totalMins % 1440) / 60);
    const mins = totalMins % 60;
    if (days > 0) return `En ${days}d ${hours}h`;
    if (hours > 0) return `En ${hours}h ${mins}m`;
    return `En ${mins} min`;
  }

  function formatStatusLabel(m) {
    if (m.status === 'live') return `EN VIVO · ${m.statusText || 'Juego'}`;
    if (m.status === 'finished') return m.statusText || 'Finalizado';
    const timePart = m.timeArg && m.timeArg !== '00:00' ? ` · ${m.timeArg}` : '';
    return `${m.dayArg || m.dateArg || 'Programado'}${timePart}`;
  }

  function findTeamMeta(teamName) {
    if (!state.data) return null;
    const target = norm(teamName);
    const dir = state.data.teamsDirectory || [];
    const hit = dir.find((t) => norm(t.name) === target || norm(t.key) === target);
    if (hit) return hit;
    const urbaClubs = (state.data.urba && state.data.urba.clubs) || [];
    return urbaClubs.find((c) => norm(c.name) === target || norm(c.key) === target) || null;
  }

  function getUrbaClubLocation(club) {
    if (!club) return '';
    const parts = [club.cancha, club.sede].filter(Boolean);
    if (!parts.length) return 'Buenos Aires · URBA';
    if (parts.length === 2 && norm(parts[0]) === norm(parts[1])) return parts[0];
    return parts.join(' · ');
  }

  async function overlayLiveFeeds() {
    if (!state.data) return;
    try {
      const now = Date.now();
      const start = new Date(now - 2 * 86400000).toISOString().slice(0, 10);
      const end = new Date(now + 7 * 86400000).toISOString().slice(0, 10);
      const [wrRes, espnRes, newsEsRes, newsEnRes] = await Promise.all([
        fetch(`https://api.wr-rims-prod.pulselive.com/rugby/v3/match?startDate=${start}&endDate=${end}&pageSize=100&sort=asc`).then(r => r.ok ? r.json() : null).catch(() => null),
        fetch('https://site.api.espn.com/apis/site/v2/sports/rugby/scorepanel').then(r => r.ok ? r.json() : null).catch(() => null),
        fetch('https://api.wr-rims-prod.pulselive.com/content/worldrugby/text/es?pageSize=20').then(r => r.ok ? r.json() : null).catch(() => null),
        fetch('https://api.wr-rims-prod.pulselive.com/content/worldrugby/text/en?pageSize=20').then(r => r.ok ? r.json() : null).catch(() => null)
      ]);
      const fmtDate = (ms) => {
        if (!ms) return { dateArg: '', dayArg: '', timeArg: '' };
        const d = new Date(ms);
        const parts = new Intl.DateTimeFormat('en-GB', {
          timeZone: 'America/Argentina/Buenos_Aires',
          day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false
        }).formatToParts(d).reduce((a, p) => { a[p.type] = p.value; return a; }, {});
        return {
          dayArg: `${parts.day}/${parts.month}/${parts.year}`,
          timeArg: `${parts.hour}:${parts.minute}`,
          dateArg: `${parts.day}/${parts.month}/${parts.year} · ${parts.hour}:${parts.minute}`
        };
      };
      const norm = (s) => (s || '').toLowerCase().replace(/[^a-z0-9]/g, '').replace(/rugby|club|7s/g, '').slice(0, 16);
      const sig = (m) => {
        const h = norm(m.home && m.home.name);
        const a = norm(m.away && m.away.name);
        const d = (m.dayArg || (m.dateArg || '').slice(0, 10));
        const is7 = (m.group === 'sevens') || /7s|sevens|svns/i.test(`${m.leagueName || ''} ${(m.home && m.home.name) || ''}`);
        return `${h}_vs_${a}_${d}${is7 ? '_7s' : ''}`;
      };
      const wrLive = [];
      if (wrRes && Array.isArray(wrRes.content)) {
        for (const m of wrRes.content) {
          const tms = m.teams || [];
          if (tms.length < 2) continue;
          const stRaw = String(m.status || 'U').toUpperCase();
          const millis = (m.time && m.time.millis) || 0;
          let st = 'scheduled';
          if (['C', 'LFT', 'FT'].includes(stRaw)) st = 'finished';
          else if (stRaw.startsWith('L') || ['1H', '2H', 'HT', 'ET'].includes(stRaw)) st = (millis && (now - millis) > 6 * 3600000) ? 'finished' : 'live';
          const evLabel = (m.events && m.events[0] && m.events[0].label) || m.competition || 'World Rugby';
          const sport = String(m.sport || (m.events && m.events[0] && m.events[0].sport) || '').toLowerCase();
          const is7s = ['mrs', 'wrs'].includes(sport) || /sevens|svns|7s/i.test(evLabel + ' ' + tms[0].name);
          const tm = fmtDate(millis);
          const sc = m.scores || [];
          const clk = (m.clock && m.clock.label) || '';
          const pMap = { L1: '1er Tiempo', L2: '2do Tiempo', LHT: 'Entretiempo', LET: 'Tiempo extra' };
          wrLive.push({
            id: `wr:${m.matchId}`,
            source: 'wr',
            eventId: String(m.matchId),
            leagueId: sport || 'wr',
            leagueName: evLabel,
            leagueShort: evLabel.replace("Men's Sevens Series 2027", 'SVNS 7s M').replace("Women's Sevens Series 2027", 'SVNS 7s F'),
            phase: String(m.eventPhase || '').replace(/Pool\s+Pool/g, 'Pool').trim(),
            group: is7s ? 'sevens' : 'nations',
            category: 'Selecciones',
            status: st,
            statusText: st === 'live' ? `${pMap[stRaw] || 'EN VIVO'}${clk ? ' · ' + clk : ''}` : (st === 'finished' ? 'Finalizado' : 'Programado'),
            isArgentina: /argentina/i.test(tms[0].name + ' ' + tms[1].name),
            venue: m.venue || {},
            ts: Math.floor(millis / 1000),
            ...tm,
            home: { name: tms[0].name, abbr: tms[0].abbreviation || '', crestUrl: '', flagUrl: '', score: st === 'scheduled' ? null : (sc[0] ?? null), lineup: [] },
            away: { name: tms[1].name, abbr: tms[1].abbreviation || '', crestUrl: '', flagUrl: '', score: st === 'scheduled' ? null : (sc[1] ?? null), lineup: [] },
            scoring: [], cards: [], officials: []
          });
        }
      }
      const existing = state.data.matches || [];
      const bySig = new Map();
      for (const m of existing) bySig.set(sig(m), m);
      for (const liveM of wrLive) {
        const s = sig(liveM);
        const prev = bySig.get(s);
        if (prev) {
          bySig.set(s, {
            ...prev,
            id: prev.id || liveM.id,
            eventId: liveM.eventId || prev.eventId,
            source: liveM.source || prev.source,
            status: liveM.status,
            statusText: liveM.statusText,
            home: { ...prev.home, score: liveM.home.score ?? prev.home.score },
            away: { ...prev.away, score: liveM.away.score ?? prev.away.score }
          });
        } else {
          bySig.set(s, liveM);
        }
      }
      const merged = Array.from(bySig.values());
      state.data.matches = merged;
      state.data.statusCounts = {
        live: merged.filter(x => x.status === 'live').length,
        scheduled: merged.filter(x => x.status === 'scheduled').length,
        finished: merged.filter(x => x.status === 'finished').length
      };
      const liveNewsItems = [];
      for (const [bundle, lang] of [[newsEsRes, 'ES'], [newsEnRes, 'EN']]) {
        if (bundle && Array.isArray(bundle.content)) {
          for (const c of bundle.content) {
            if (!c.title) continue;
            const dInfo = fmtDate(c.publishFrom || (c.date ? Date.parse(c.date) : 0));
            liveNewsItems.push({
              id: `wr_live_${lang}_${c.id}`,
              title: c.title,
              summary: c.description || c.subtitle || c.title,
              source: `World Rugby ${lang}`,
              url: (c.canonicalUrl || `https://www.world.rugby/news/${c.id}`).replace('http://', 'https://'),
              imageUrl: c.imageUrl || (c.leadMedia && c.leadMedia.imageUrl) || '',
              topicLabel: /svns|sevens|7s/i.test(c.title + ' ' + (c.description || '')) ? 'Seven / SVNS' : 'Internacional',
              dateArg: dInfo.dateArg
            });
          }
        }
      }
      if (liveNewsItems.length) {
        const seenTitle = new Set();
        const combined = [];
        for (const n of [...liveNewsItems, ...(state.data.news || [])]) {
          const tk = (n.title || '').toLowerCase().replace(/[^a-z0-9]/g, '').slice(0, 60);
          if (!tk || !seenTitle.has(tk)) {
            if (tk) seenTitle.add(tk);
            combined.push(n);
          }
        }
        state.data.news = combined;
      }
      render();
      if (state.sheetMatchId && !els.detailSheet.classList.contains('hidden')) {
        openMatchSheet(state.sheetMatchId, state.sheetTab);
      }
    } catch (_) {}
  }

  async function enrichWebWrMatch(m) {
    if (!m || !m.eventId || m.source !== 'wr') return;
    try {
      const base = `https://api.wr-rims-prod.pulselive.com/rugby/v3/match/${m.eventId}`;
      const [sm, tl] = await Promise.all([
        fetch(`${base}/summary`).then(r => r.ok ? r.json() : null).catch(() => null),
        fetch(`${base}/timeline`).then(r => r.ok ? r.json() : null).catch(() => null)
      ]);
      const pMap = {};
      if (sm) {
        if (Array.isArray(sm.officials)) {
          m.officials = sm.officials.map(o => ({
            name: (o.official && o.official.name && o.official.name.display) || '',
            role: o.position || 'Oficial',
            country: (o.official && o.official.country) || ''
          })).filter(o => o.name);
        }
        const tms = sm.teams || [];
        ['home', 'away'].forEach((sk, idx) => {
          const tb = tms[idx];
          const tlObj = tb && tb.teamList;
          if (tlObj && Array.isArray(tlObj.list) && tlObj.list.length) {
            const caps = new Set((tlObj.captainIds || []).map(String));
            m[sk].lineup = tlObj.list.map(e => {
              const pl = e.player || {};
              const nm = (pl.name && pl.name.display) || '';
              if (pl.id) pMap[String(pl.id)] = nm;
              if (pl.altId) pMap[String(pl.altId)] = nm;
              return {
                jersey: String(e.number || '-'),
                name: nm,
                position: e.positionLabel || '',
                captain: caps.has(String(pl.id)) || caps.has(String(pl.altId)),
                caps: pl.caps || null
              };
            }).filter(x => x.name);
          }
        });
      }
      if (tl && Array.isArray(tl.timeline)) {
        const sc = [];
        for (const ev of tl.timeline) {
          const tc = String(ev.type || '');
          const tlLbl = String(ev.typeLabel || '');
          if (tc === 'MS' || /miss|change match/i.test(tc + ' ' + tlLbl)) continue;
          const sk = ev.teamIndex === 1 ? 'away' : 'home';
          const pid = String(ev.playerId || ev.playerAltId || '');
          const minL = (ev.time && ev.time.label) || '';
          const tp = /try|T5/i.test(tc + tlLbl) ? 'Try' : (/conv|C2/i.test(tc + tlLbl) ? 'Conv' : (/pen|P3/i.test(tc + tlLbl) ? 'Penal' : (/drop|DG/i.test(tc + tlLbl) ? 'Drop' : '')));
          if (tp) sc.push({ team: sk, teamName: m[sk].name, type: tp, player: pMap[pid] || '', minute: minL });
        }
        if (sc.length) m.scoring = sc;
      }
    } catch (_) {}
  }

  async function loadData(force = false) {
    if (force && els.refreshBtn) {
      els.refreshBtn.textContent = 'Actualizando...';
      els.refreshBtn.disabled = true;
    }
    const bust = `?t=${Date.now()}`;
    try {
      let resp = await fetch(DATA_URL + bust, { cache: 'no-store' });
      if (!resp.ok) resp = await fetch(FALLBACK_URL + bust, { cache: 'no-store' });
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      state.data = await resp.json();
      if (state.data && state.data.urba && Array.isArray(state.data.urba.championships) && state.data.urba.championships.length) {
        const exists = state.data.urba.championships.some((c) => c.id === state.urbaChampId);
        if (!exists) state.urbaChampId = state.data.urba.championships[0].id;
      }
      els.offlineBar.classList.add('hidden');
      render();
      overlayLiveFeeds();
    } catch (err) {
      if (!state.data) {
        els.main.innerHTML = `<div class="empty-box">No se pudieron cargar los datos de rugby (${esc(err.message)}). Revisá tu conexión y tocá Actualizar.</div>`;
      } else {
        els.offlineBar.classList.remove('hidden');
      }
    } finally {
      if (els.refreshBtn) {
        els.refreshBtn.textContent = 'Actualizar';
        els.refreshBtn.disabled = false;
      }
    }
  }

  function setTab(tabName) {
    state.tab = tabName;
    els.navItems.forEach((btn) => {
      btn.classList.toggle('active', btn.dataset.tab === tabName);
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
    render();
  }

  function toggleDaylight() {
    state.daylight = !state.daylight;
    document.documentElement.classList.toggle('daylight', state.daylight);
    localStorage.setItem('rugbyProTheme', state.daylight ? 'daylight' : 'stadium');
    render();
  }

  function renderMatchCard(m) {
    const isLive = m.status === 'live';
    const isArg = Boolean(m.isArgentina);
    const hasScores = m.status !== 'scheduled' && m.home && m.away && m.home.score != null && m.away.score != null;
    const hScore = hasScores ? Number(m.home.score) : null;
    const aScore = hasScores ? Number(m.away.score) : null;
    const hWin = hasScores && hScore > aScore;
    const aWin = hasScores && aScore > hScore;

    const hMeta = findTeamMeta(m.home.name);
    const aMeta = findTeamMeta(m.away.name);
    const hFlag = m.home.flagUrl || (hMeta && hMeta.flagUrl) || '';
    const aFlag = m.away.flagUrl || (aMeta && aMeta.flagUrl) || '';

    const hCoachC = cleanCoach(m.home.coach); const aCoachC = cleanCoach(m.away.coach); const hSub = hCoachC ? `DT: ${hCoachC}` : (m.home.worldRank ? `Ranking #${m.home.worldRank}` : (hMeta && hMeta.division ? `URBA ${hMeta.division}` : ''));
    const aSub = aCoachC ? `DT: ${aCoachC}` : (m.away.worldRank ? `Ranking #${m.away.worldRank}` : (aMeta && aMeta.division ? `URBA ${aMeta.division}` : ''));

    const venueLabel = (m.venue && (m.venue.name || m.venue.city))
      ? [m.venue.name, m.venue.city].filter(Boolean).join(', ')
      : (m.phase || 'Sede oficial');

    const stateClass = isLive ? 'state-live' : (m.status === 'scheduled' ? 'state-scheduled' : 'state-finished');
    const leagueTitle = m.phase ? `${m.leagueShort || m.leagueName} · ${m.phase}` : (m.leagueShort || m.leagueName);

    return `
      <button class="match-card ${isLive ? 'is-live' : ''} ${isArg ? 'is-arg' : ''}" data-match-id="${esc(m.id)}" type="button">
        <div class="card-kicker">
          <span class="kicker-league">${esc(leagueTitle)}</span>
          <span class="kicker-state ${stateClass}">${esc(formatStatusLabel(m))}</span>
        </div>
        <div class="board-rows">
          <div class="board-row">
            <div class="team-left">
              ${renderTeamVisual(m.home.crestUrl, hFlag, m.home.name)}
              <div class="team-info">
                <div class="team-name">${esc(m.home.name)}</div>
                ${hSub ? `<div class="team-sub">${esc(hSub)}</div>` : ''}
              </div>
            </div>
            <div class="row-score ${hWin ? 'win' : (!hasScores ? 'muted' : '')}">
              ${hasScores ? esc(hScore) : '—'}
            </div>
          </div>
          <div class="board-row">
            <div class="team-left">
              ${renderTeamVisual(m.away.crestUrl, aFlag, m.away.name)}
              <div class="team-info">
                <div class="team-name">${esc(m.away.name)}</div>
                ${aSub ? `<div class="team-sub">${esc(aSub)}</div>` : ''}
              </div>
            </div>
            <div class="row-score ${aWin ? 'win' : (!hasScores ? 'muted' : '')}">
              ${hasScores ? esc(aScore) : '—'}
            </div>
          </div>
        </div>
        <div class="card-foot">
          <span class="foot-venue">${esc(venueLabel)}</span>
          <span class="foot-cta">${m.status === 'scheduled' && m.ts ? esc(countdownText(m.ts)) : 'Ficha ›'}</span>
        </div>
      </button>`;
  }

  function renderStadiumHero(d) {
    const xv = d.argentinaNextXV || d.argentinaNext;
    const s7 = d.argentinaNext7s;
    const coaches = (d.argentinaHub && d.argentinaHub.coaches) || { xv: 'Felipe Contepomi', sevens: 'Santiago Gómez Cora' };
    const st = d.statusCounts || {};
    const clubsCount = (d.urba && d.urba.clubs && d.urba.clubs.length) || 99;
    const newsList = d.news || [];
    const dirCount = (d.teamsDirectory && d.teamsDirectory.length) || 0;
    const topStory = newsList.find((n) => n.topic === 'alineaciones' || n.topic === 'fichajes') || newsList[0];

    const tickerHtml = topStory ? `
      <div class="hero-ticker">
        <a class="hero-ticker-left" href="${esc(topStory.url)}" target="_blank" rel="noopener noreferrer" title="${esc(topStory.title)}">
          <span class="hero-ticker-tag">${esc((topStory.topicLabel || topStory.source || 'ÚLTIMA HORA').toUpperCase())}</span>
          <span class="hero-ticker-title">${esc(topStory.title)}</span>
        </a>
        <button class="hero-ticker-cta" data-jump-tab="news" type="button">${newsList.length} noticias ›</button>
      </div>` : '';

    const mainHtml = xv ? `
      <div class="hero-main-col">
        <div>
          <div class="hero-kicker">LOS PUMAS XV · <span>${esc(xv.leagueName || 'Test Match Oficial')}</span></div>
          <h1 class="hero-display" style="margin-top:8px;">${esc(xv.home.name)} vs ${esc(xv.away.name)}</h1>
        </div>
        <div class="hero-crest-row">
          ${renderTeamVisual(xv.home.crestUrl, xv.home.flagUrl, xv.home.name, 'xl')}
          <span class="hero-vs">VS</span>
          ${renderTeamVisual(xv.away.crestUrl, xv.away.flagUrl, xv.away.name, 'xl')}
        </div>
        <div class="hero-meta-strip">
          <span><strong>${esc(xv.dateArg)}</strong> · ${esc(countdownText(xv.ts))}</span>
          <span aria-hidden="true">·</span>
          <span>${esc([xv.venue && xv.venue.name, xv.venue && xv.venue.city].filter(Boolean).join(', ') || 'Estadio Oficial')}</span>
          <span aria-hidden="true">·</span>
          <span>Coach: <strong>${esc(coaches.xv)}</strong></span>
        </div>
        <div>
          <button class="hero-btn" data-match-id="${esc(xv.id)}" type="button">Ver ficha del partido ›</button>
        </div>
      </div>` : '';

    const sideHtml = s7 ? `
      <div class="hero-side-col">
        <div>
          <div class="hero-kicker" style="color:#e5b85c;">LOS PUMAS 7s · <span>${esc(s7.leagueShort || s7.leagueName)}</span></div>
          <h2 class="side-title" style="margin-top:8px;">${esc(s7.home.name)} vs ${esc(s7.away.name)}</h2>
        </div>
        <div class="hero-crest-row">
          ${renderTeamVisual(s7.home.crestUrl, s7.home.flagUrl, s7.home.name, 'lg')}
          <span class="hero-vs">VS</span>
          ${renderTeamVisual(s7.away.crestUrl, s7.away.flagUrl, s7.away.name, 'lg')}
        </div>
        <div class="hero-meta-strip">
          <span><strong>${esc(s7.dateArg)}</strong></span>
          <span aria-hidden="true">·</span>
          <span>${esc([s7.venue && s7.venue.name, s7.venue && s7.venue.city].filter(Boolean).join(', ') || 'HSBC SVNS')}</span>
          <span aria-hidden="true">·</span>
          <span>Coach: <strong>${esc(coaches.sevens)}</strong></span>
        </div>
        <div>
          <button class="hero-btn ghost" data-match-id="${esc(s7.id)}" type="button">Ver detalle 7s ›</button>
        </div>
      </div>` : '';

    return `
      <section class="stadium-hero">
        <img src="./assets/hero_stadium.jpg" alt="Estadio Internacional de Rugby" class="stadium-bg" referrerpolicy="no-referrer" />
        <div class="stadium-scrim">
          ${tickerHtml}
          <div class="hero-split">
            ${mainHtml}
            ${sideHtml}
          </div>
        </div>
      </section>
      <div class="kpi-strip">
        <div class="kpi-cell">
          <div class="kpi-num accent">${esc(d.totalMatches || 0)}</div>
          <div class="kpi-label">${st.live ? `${st.live} en vivo · ` : ''}${st.scheduled || 0} próximos · ${st.finished || 0} cerrados</div>
        </div>
        <div class="kpi-cell">
          <div class="kpi-num sky">${esc(clubsCount)}</div>
          <div class="kpi-label">Clubes URBA Buenos Aires (8 divisiones)</div>
        </div>
        <div class="kpi-cell">
          <div class="kpi-num">${esc(newsList.length)}</div>
          <div class="kpi-label">Noticias, bajas y fichajes · Sync diario</div>
        </div>
        <div class="kpi-cell">
          <div class="kpi-num">${esc(dirCount)}</div>
          <div class="kpi-label">Escudos y banderas · ${esc(d.generatedAtArg || '')}</div>
        </div>
      </div>`;
  }

  function renderHomePulse(d) {
    const allNews = d.news || [];
    if (!allNews.length) return '';
    // Pick up to 4 diverse highlights prioritizing lineups, transfers & Pumas
    const picked = [];
    const seenIds = new Set();
    for (const wanted of ['alineaciones', 'fichajes', 'pumas']) {
      const hit = allNews.find((n) => (n.topic === wanted || (n.tags || []).includes(wanted)) && !seenIds.has(n.id));
      if (hit) {
        picked.push(hit);
        seenIds.add(hit.id);
      }
    }
    for (const n of allNews) {
      if (picked.length >= 4) break;
      if (!seenIds.has(n.id)) {
        picked.push(n);
        seenIds.add(n.id);
      }
    }

    const newsItemsHtml = picked.map((n) => `
      <a class="pulse-news-item" href="${esc(n.url)}" target="_blank" rel="noopener noreferrer">
        <div class="pulse-thumb">
          ${n.imageUrl
            ? `<img src="${esc(n.imageUrl)}" alt="${esc(n.title)}" loading="lazy" referrerpolicy="no-referrer" onerror="this.style.display='none';" />`
            : ''}
        </div>
        <div class="pulse-news-info">
          <div class="pulse-news-meta">${esc(n.topicLabel || 'Actualidad')} <span>· ${esc(n.dateArg || 'Hoy')}</span></div>
          <div class="pulse-news-title">${esc(n.title)}</div>
        </div>
      </a>`).join('');

    const menRk = (d.rankings && d.rankings.men) || [];
    const topFive = menRk.slice(0, 5);
    const argRk = menRk.find((r) => norm(r.team) === 'argentina');
    const rkShow = argRk && !topFive.some((r) => norm(r.team) === 'argentina') ? [...topFive, argRk] : menRk.slice(0, 6);

    const rkHtml = rkShow.map((r) => {
      const diff = r.prevPos && r.pos ? r.prevPos - r.pos : 0;
      const diffTxt = diff > 0 ? `▲${diff}` : (diff < 0 ? `▼${Math.abs(diff)}` : '—');
      const diffCol = diff > 0 ? 'var(--turf)' : (diff < 0 ? 'var(--crimson)' : 'var(--text-dim)');
      const isArg = norm(r.team) === 'argentina';
      return `
        <div class="pulse-rank-row ${isArg ? 'is-arg' : ''}">
          <div style="display:flex;align-items:center;gap:10px;min-width:0;">
            <span class="mono-num" style="font-weight:800;min-width:22px;color:${r.pos <= 4 ? 'var(--gold)' : 'var(--text-dim)'};">#${r.pos}</span>
            ${r.flagUrl ? `<img class="flag-inline" src="${esc(r.flagUrl)}" alt="${esc(r.team)}" loading="lazy" referrerpolicy="no-referrer" />` : ''}
            ${renderTeamVisual(r.crestUrl, '', r.team, 'sm')}
            <span style="font-weight:${isArg ? '800' : '600'};font-size:0.86rem;color:${isArg ? 'var(--sky)' : 'var(--text)'};white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${esc(r.team)}</span>
          </div>
          <div style="display:flex;align-items:center;gap:10px;flex-shrink:0;">
            <span class="mono-num" style="font-size:0.8rem;font-weight:700;">${Number(r.pts || 0).toFixed(2)}</span>
            <span class="mono-num" style="font-size:0.74rem;font-weight:700;color:${diffCol};min-width:22px;text-align:right;">${diffTxt}</span>
          </div>
        </div>`;
    }).join('');

    return `
      <section class="pulse-grid" aria-label="Resumen diario y ranking">
        <div class="pulse-panel">
          <div class="pulse-head">
            <h2>Pulso Diario · Bajas, Fichajes y Noticias</h2>
            <button class="action-btn" data-jump-tab="news" type="button">Ver ${allNews.length} ›</button>
          </div>
          ${newsItemsHtml}
        </div>
        <div class="pulse-panel">
          <div class="pulse-head">
            <h2>World Rugby Top Ranking</h2>
            <button class="action-btn" data-jump-ranking="1" type="button">Completo ›</button>
          </div>
          ${rkHtml}
        </div>
      </section>`;
  }

  function renderToolbar(searchId, searchVal, placeholder) {
    return `
      <div class="toolbar-row">
        <input id="${searchId}" class="search-box" type="search" placeholder="${esc(placeholder)}" value="${esc(searchVal)}" />
        <button id="themeToggleBtn" class="theme-btn" type="button" title="Cambiar contraste Estadio / Día">
          ${state.daylight ? 'Modo Estadio' : 'Modo Día'}
        </button>
      </div>`;
  }


  function renderCompStandings(comp) {
    const tables = comp.standings || [];
    if (!tables.length) {
      return `<div class="empty-box">Sin tabla de posiciones activa en esta ventana para ${esc(comp.shortName)}.</div>`;
    }
    return tables.map((tbl) => `
      <div class="section-head" style="margin:12px 0 8px;">
        <h3 class="section-title" style="font-size:1.15rem;">${esc(tbl.groupName)}</h3>
        <span class="section-sub">Temporada ${esc(tbl.seasonLabel || comp.season)}</span>
      </div>
      <div class="table-wrap" style="margin-bottom:14px;">
        <table class="rank-table">
          <thead>
            <tr><th>#</th><th>Equipo</th><th>PJ</th><th>G</th><th>E</th><th>P</th><th>Dif</th><th>Bono</th><th>Pts</th></tr>
          </thead>
          <tbody>
            ${(tbl.rows || []).map((r) => `
              <tr>
                <td class="mono-num" style="font-weight:700;color:${r.pos <= 4 ? 'var(--gold)' : 'var(--text)'};">${r.pos}</td>
                <td>
                  <div style="display:flex;align-items:center;gap:8px;min-width:150px;">
                    ${renderTeamVisual(r.crestUrl, r.flagUrl, r.name, 'sm')}
                    <span style="font-weight:600;color:var(--text);">${esc(r.name)}</span>
                  </div>
                </td>
                <td class="mono-num">${r.played}</td>
                <td class="mono-num">${r.won}</td>
                <td class="mono-num">${r.tied}</td>
                <td class="mono-num">${r.lost}</td>
                <td class="mono-num" style="color:${String(r.diff || '').startsWith('-') ? 'var(--crimson)' : 'var(--turf)'};">${esc(r.diff)}</td>
                <td class="mono-num">${r.bonus}</td>
                <td class="mono-num" style="font-weight:800;color:var(--gold);">${r.pts}</td>
              </tr>`).join('')}
          </tbody>
        </table>
      </div>
    `).join('');
  }

  function renderCompetitionSpotlight(comp, matchCount) {
    if (!comp) return '';
    const hasStandings = Array.isArray(comp.standings) && comp.standings.length > 0;
    return `
      <section class="comp-spotlight">
        <div class="comp-spot-top">
          <div style="min-width:0;flex:1;">
            <div class="comp-card-kicker ${comp.isArgentinaCore ? 'arg' : ''}">
              ${esc(comp.category)} · ${esc(comp.region)} · ID: ${esc(comp.id)}
            </div>
            <h2 class="comp-spot-title">${esc(comp.name)}</h2>
            <div class="comp-spot-meta">
              <strong>${esc(comp.teamsCount)} equipos</strong> (${esc(comp.teamsNote || '')}) · Temporada: <strong>${esc(comp.season)}</strong> · ${matchCount} partidos en agenda
            </div>
            ${comp.notes ? `<div style="font-size:0.78rem;color:var(--text-soft);margin-top:6px;">${esc(comp.notes)}</div>` : ''}
          </div>
          ${hasStandings ? `
            <div class="sub-seg" style="margin-bottom:0;min-width:max-content;">
              <button class="sub-btn ${state.compViewMode === 'matches' ? 'active' : ''}" data-comp-view="matches" type="button">Partidos (${matchCount})</button>
              <button class="sub-btn ${state.compViewMode === 'standings' ? 'active' : ''}" data-comp-view="standings" type="button">Tabla de Posiciones</button>
            </div>` : ''}
        </div>
      </section>`;
  }

  function renderMatchesTab(d) {
    const gc = d.groupCounts || {};
    const comps = d.competitions || [];
    const clubCount = (gc.top14 || 0) + (gc.clubs || 0) + (gc.sra || 0) + (gc.urba || 0);
    const nationsCount = (gc.nations || 0) + (gc.worldcup || 0);
    const groupTabs = [
      { id: 'all', label: `Todos (${d.totalMatches || 0})` },
      { id: 'arg', label: `Argentina & URBA (${(d.argentinaMatches || []).length})` },
      { id: 'cat-clubes', label: `Ligas Clubes (${clubCount})` },
      { id: 'cat-copas', label: `Copas Europeas (${gc.copas || 0})` },
      { id: 'cat-selecciones', label: `Selecciones (${nationsCount})` },
      { id: 'sevens', label: `Seven SVNS (${gc.sevens || 0})` },
    ];

    const visibleComps = comps.filter((c) => {
      if (state.matchGroup === 'arg') return c.isArgentinaCore || c.id === 'sa-super-rugby-americas' || c.id === 'trc-rugby-championship';
      if (state.matchGroup === 'cat-clubes') return c.category === 'Clubes';
      if (state.matchGroup === 'cat-copas') return c.category === 'Copas';
      if (state.matchGroup === 'cat-selecciones') return c.category === 'Selecciones';
      if (state.matchGroup === 'sevens') return false;
      return true;
    });

    const activeComp = state.matchCompId !== 'all' ? comps.find((c) => c.id === state.matchCompId) : null;

    const statusTabs = [
      { id: 'all', label: 'Todos' },
      { id: 'live', label: `En Vivo (${(d.statusCounts && d.statusCounts.live) || 0})` },
      { id: 'scheduled', label: `Próximos (${(d.statusCounts && d.statusCounts.scheduled) || 0})` },
      { id: 'finished', label: `Finalizados (${(d.statusCounts && d.statusCounts.finished) || 0})` },
    ];

    const q = norm(state.search);
    const filtered = (d.matches || []).filter((m) => {
      if (state.matchCompId !== 'all') {
        if (state.matchCompId === 'ar-los-pumas') {
          if (!(m.isArgentina && m.group !== 'urba')) return false;
        } else if (m.compId !== state.matchCompId) {
          return false;
        }
      } else {
        if (state.matchGroup === 'arg' && !m.isArgentina) return false;
        if (state.matchGroup === 'cat-clubes' && !['urba', 'top14', 'clubs', 'sra', 'arg'].includes(m.group)) return false;
        if (state.matchGroup === 'cat-copas' && m.group !== 'copas') return false;
        if (state.matchGroup === 'cat-selecciones' && !['nations', 'worldcup'].includes(m.group)) return false;
        if (state.matchGroup === 'sevens' && m.group !== 'sevens') return false;
      }
      if (state.matchStatus !== 'all' && m.status !== state.matchStatus) return false;
      if (q) {
        const hay = norm(`${m.home.name} ${m.away.name} ${m.leagueName} ${m.leagueShort} ${m.phase || ''} ${m.venue && m.venue.name} ${m.venue && m.venue.city}`);
        if (!hay.includes(q)) return false;
      }
      return true;
    });

    const compRailHtml = visibleComps.length ? `
      <div class="comp-rail" aria-label="Competencias oficiales">
        <button class="comp-btn ${state.matchCompId === 'all' ? 'active' : ''}" data-match-comp="all" type="button">
          <span class="comp-btn-title">Todas las competencias</span>
          <span class="comp-btn-meta">${visibleComps.length} torneos en categoría</span>
        </button>
        ${visibleComps.map((c) => `
          <button class="comp-btn ${state.matchCompId === c.id ? 'active' : ''}" data-match-comp="${esc(c.id)}" type="button">
            <span class="comp-btn-title">${esc(c.shortName)}</span>
            <span class="comp-btn-meta">${c.teamsCount} eq · ${esc(c.season)} · ${c.matchesCount} part.</span>
          </button>
        `).join('')}
      </div>` : '';

    const spotHtml = activeComp ? renderCompetitionSpotlight(activeComp, filtered.length) : '';
    const showStandings = Boolean(activeComp && state.compViewMode === 'standings' && (activeComp.standings || []).length);

    const cardsHtml = showStandings
      ? renderCompStandings(activeComp)
      : (filtered.length
        ? `<div class="cards-grid">${filtered.slice(0, 96).map(renderMatchCard).join('')}</div>`
        : `<div class="empty-box">No hay partidos en vivo o programados esta semana para "${esc((activeComp && activeComp.name) || state.search || state.matchGroup)}". ${(activeComp && activeComp.standings && activeComp.standings.length) ? 'Podés revisar la Tabla de Posiciones arriba.' : 'Probá otro filtro arriba.'}</div>`);

    const showHero = (state.matchGroup === 'all' || state.matchGroup === 'arg') && state.matchCompId === 'all' && !q;
    return `
      ${showHero ? renderStadiumHero(d) : ''}
      ${renderToolbar('matchSearchInput', state.search, 'Buscar equipo, país, Top 14 Francia, URC, SRA, URBA o estadio...')}
      <div class="filter-scroll">
        ${groupTabs.map((t) => `<button class="seg-tab ${state.matchGroup === t.id ? 'active' : ''}" data-match-group="${t.id}" type="button">${esc(t.label)}</button>`).join('')}
      </div>
      ${compRailHtml}
      ${spotHtml}
      ${!showStandings ? `<div class="sub-seg">
        ${statusTabs.map((s) => `<button class="sub-btn ${state.matchStatus === s.id ? 'active' : ''}" data-match-status="${s.id}" type="button">${esc(s.label)}</button>`).join('')}
      </div>` : ''}
      ${cardsHtml}
      ${showHero ? renderHomePulse(d) : ''}`;
  }
  function renderPumasTab(d) {
    const hub = d.argentinaHub || {};
    const roster = hub.xvRoster || [];
    const tournaments = hub.tournaments || [];
    const argMatches = d.argentinaMatches || [];
    const confirmed = d.confirmedLineups || [];
    const pumasNews = (d.news || []).filter((n) => (n.tags || []).includes('pumas') || n.category === 'pumas').slice(0, 6);

    const forwards = roster.filter((p) => p.starter && Number(p.jersey) <= 8);
    const backs = roster.filter((p) => p.starter && Number(p.jersey) >= 9);
    const bench = roster.filter((p) => !p.starter);

    const tournamentsHtml = tournaments.map((t) => `
      <div class="team-card" style="cursor:default;flex-direction:column;align-items:flex-start;gap:4px;">
        <div style="font-size:0.74rem;font-weight:700;color:var(--sky);">${esc(t.team)} · ${esc(t.coach)}</div>
        <div style="font-family:var(--font-display);font-size:1.25rem;font-weight:800;color:var(--text);">${esc(t.name)}</div>
        <div style="font-size:0.82rem;color:var(--text-soft);">${esc(t.status)}</div>
      </div>`).join('');

    const playerRow = (p) => `
      <div class="player-row">
        <span><span class="jersey-num">${esc(p.jersey)}</span>${esc(p.name)}${p.captain ? ' (C)' : ''}</span>
        <span style="color:var(--text-dim);font-size:0.74rem;">${esc(p.position)}${p.caps ? ` · ${p.caps} caps` : ''}</span>
      </div>`;

    const rosterHtml = roster.length ? `
      <div class="pitch-board">
        <div class="tactical-grid">
          <div class="tactical-col">
            <h4>Forwards Titulares (1–8)</h4>
            ${forwards.map(playerRow).join('') || '<div class="empty-box">Sin datos</div>'}
          </div>
          <div class="tactical-col">
            <h4>Backs Titulares (9–15)</h4>
            ${backs.map(playerRow).join('') || '<div class="empty-box">Sin datos</div>'}
          </div>
          <div class="tactical-col">
            <h4 style="color:var(--sky);">Banco e Impacto (16–23)</h4>
            ${bench.map(playerRow).join('') || '<div class="empty-box">Banco por confirmar</div>'}
          </div>
        </div>
      </div>` : `<div class="empty-box">Alineación de Los Pumas se actualiza al confirmarse el próximo Test Match.</div>`;

    const confirmedHtml = confirmed.length ? `
      <div class="section-head">
        <h2 class="section-title">Alineaciones Oficiales Verificadas</h2>
        <span class="section-sub">${confirmed.length} planteles XV recientes · Tocá para ver la formación completa</span>
      </div>
      <div class="dir-grid">
        ${confirmed.slice(0, 9).map((cl) => `
          <button class="lineup-mini-card" data-match-id="${esc(cl.matchId)}" data-open-lineup="1" type="button">
            <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;width:100%;">
              <span style="font-size:0.72rem;font-weight:700;color:var(--gold);overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${esc(cl.leagueName)}</span>
              <span class="mono-num" style="font-size:0.7rem;color:var(--text-dim);white-space:nowrap;">${esc(cl.dateArg)}</span>
            </div>
            <div style="display:flex;align-items:center;gap:11px;min-width:0;">
              ${renderTeamVisual(cl.crestUrl, cl.flagUrl, cl.teamName, 'lg')}
              <div style="min-width:0;flex:1;">
                <div style="font-weight:700;font-size:0.94rem;color:var(--text);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${esc(cl.teamName)}</div>
                <div style="font-size:0.75rem;color:var(--text-dim);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">vs ${esc(cl.opponentName)} · ${esc(cl.playersCount)} jugadores</div>
              </div>
            </div>
            <div style="font-size:0.74rem;color:var(--sky);font-weight:600;">Ver XV titular y banco ›</div>
          </button>`).join('')}
      </div>` : '';

    const newsHtml = pumasNews.length
      ? `<div class="news-grid">${pumasNews.map(renderNewsCard).join('')}</div>`
      : '';

    return `
      <section class="section-banner">
        <img src="./assets/pumas_banner.jpg" alt="Los Pumas Argentina Rugby" class="section-banner-img" referrerpolicy="no-referrer" />
        <div class="section-banner-scrim">
          <div class="hero-kicker">UNIÓN ARGENTINA DE RUGBY · SELECCIONES NACIONALES</div>
          <h1 class="banner-title">Los Pumas XV y Los Pumas 7s</h1>
          <p class="banner-sub">Agenda internacional, Nations Championship, HSBC SVNS, franquicias argentinas y último plantel oficial verificado.</p>
        </div>
      </section>
      <div class="section-head" style="margin-top:4px;">
        <h2 class="section-title">Campeonatos y Selecciones de Argentina</h2>
        <span class="section-sub">UAR · Franquicias · URBA</span>
      </div>
      <div class="dir-grid">${tournamentsHtml}</div>
      <div class="section-head">
        <h2 class="section-title">Agenda y Resultados de Argentina (XV &amp; 7s)</h2>
        <span class="section-sub">${argMatches.length} encuentros</span>
      </div>
      <div class="cards-grid">${argMatches.map(renderMatchCard).join('')}</div>
      <div class="section-head">
        <h2 class="section-title">Plantel Oficial — Los Pumas XV</h2>
        <span class="section-sub">Head Coach: ${esc((hub.coaches && hub.coaches.xv) || 'Felipe Contepomi')}</span>
      </div>
      ${rosterHtml}
      ${confirmedHtml}
      ${newsHtml ? `
        <div class="section-head">
          <h2 class="section-title">Últimas Noticias de Los Pumas &amp; UAR</h2>
          <button class="action-btn" data-jump-tab="news" type="button">Ver todas ›</button>
        </div>
        ${newsHtml}` : ''}`;
  }
  function renderUrbaTab(d) {
    const urba = d.urba || {};
    const champs = urba.championships || [];
    const clubs = urba.clubs || [];
    const activeChamp = champs.find((c) => c.id === state.urbaChampId) || champs[0];

    const champPills = champs.map((c) => `
      <button class="seg-tab ${activeChamp && c.id === activeChamp.id ? 'active' : ''}" data-urba-champ="${c.id}" type="button">
        ${esc(c.short)} (${(c.standings || []).length})
      </button>`).join('');

    const subTabs = [
      { id: 'standings', label: 'Tabla de Posiciones' },
      { id: 'matches', label: `Partidos y Playoff (${activeChamp ? (activeChamp.recentMatches || []).length : 0})` },
      { id: 'clubs', label: `99 Clubes de Buenos Aires (${clubs.length})` },
    ];

    let bodyHtml = '';
    if (state.urbaSubTab === 'standings' && activeChamp) {
      const rows = (activeChamp.standings || []).map((r) => `
        <tr class="${r.pos === 4 ? 'playoff-cut' : ''}" data-urba-club-id="${r.clubId}" style="cursor:pointer;">
          <td class="mono-num" style="color:${r.pos <= 4 ? 'var(--gold)' : 'var(--text-dim)'};font-weight:700;">${r.pos}</td>
          <td>
            <div style="display:flex;align-items:center;gap:10px;min-width:150px;">
              ${renderTeamVisual(r.crestUrl, '', r.name, 'sm')}
              <span style="font-weight:600;color:var(--text);">${esc(r.name)}</span>
            </div>
          </td>
          <td class="mono-num" style="font-weight:700;color:var(--gold);">${r.pts}</td>
          <td class="mono-num">${r.played}</td>
          <td class="mono-num">${r.won}</td>
          <td class="mono-num">${r.lost}</td>
          <td class="mono-num">${r.pf - r.pc >= 0 ? '+' : ''}${r.pf - r.pc}</td>
          <td class="mono-num">${r.bonus}</td>
        </tr>`).join('');

      bodyHtml = `
        <div class="section-head" style="margin-top:4px;">
          <h2 class="section-title">${esc(activeChamp.name)}</h2>
          <span class="section-sub">${activeChamp.totalRounds || 0} fechas · Tocá un club para ver cancha y sede</span>
        </div>
        <div class="table-wrap">
          <table class="rank-table">
            <thead>
              <tr><th>#</th><th>Club</th><th>Pts</th><th>PJ</th><th>G</th><th>P</th><th>Dif</th><th>Bonus</th></tr>
            </thead>
            <tbody>${rows}</tbody>
          </table>
        </div>`;
    } else if (state.urbaSubTab === 'matches' && activeChamp) {
      const ms = activeChamp.recentMatches || [];
      bodyHtml = ms.length
        ? `<div class="section-head" style="margin-top:4px;"><h2 class="section-title">${esc(activeChamp.name)} — Últimas Fechas</h2><span class="section-sub">${ms.length} cruces</span></div>
           <div class="cards-grid">${ms.map(renderMatchCard).join('')}</div>`
        : `<div class="empty-box">No hay partidos recientes en ${esc(activeChamp.name)}.</div>`;
    } else {
      const q = norm(state.urbaSearch);
      const filteredClubs = clubs.filter((c) => {
        if (!q) return true;
        return norm(`${c.name} ${c.division} ${c.sede} ${c.cancha}`).includes(q);
      });
      bodyHtml = `
        ${renderToolbar('urbaSearchInput', state.urbaSearch, 'Buscar club de Buenos Aires, división, sede o cancha...')}
        <div class="dir-grid">
          ${filteredClubs.map((c) => `
            <button class="team-card" data-urba-club-id="${c.id}" type="button">
              ${renderTeamVisual(c.crestUrl, '', c.name, 'lg')}
              <div class="team-info">
                <div class="team-name">${esc(c.name)}</div>
                <div class="team-sub" style="color:var(--gold);">${esc(c.division)}${c.standingsPos ? ` · #${c.standingsPos} (${c.pts} pts)` : ''}</div>
                <div class="team-sub">${esc(getUrbaClubLocation(c))}</div>
              </div>
            </button>`).join('')}
        </div>`;
    }

    return `
      <section class="section-banner">
        <img src="./assets/urba_pitch.jpg" alt="Rugby de Buenos Aires URBA" class="section-banner-img" referrerpolicy="no-referrer" />
        <div class="section-banner-scrim">
          <div class="hero-kicker" style="color:#e5b85c;">UNIÓN DE RUGBY DE BUENOS AIRES · OFICIAL</div>
          <h1 class="banner-title">Campeonatos y Clubes de Buenos Aires</h1>
          <p class="banner-sub">Top 14, Primera A, B, C, Segunda, Tercera, Desarrollo y Femenino · ${clubs.length} clubes afiliados con escudos oficiales verificados.</p>
        </div>
      </section>
      <div class="filter-scroll">${champPills}</div>
      <div class="sub-seg">
        ${subTabs.map((s) => `<button class="sub-btn ${state.urbaSubTab === s.id ? 'active' : ''}" data-urba-sub="${s.id}" type="button">${esc(s.label)}</button>`).join('')}
      </div>
      ${bodyHtml}`;
  }

  function renderNewsCard(n) {
    const mediaHtml = n.imageUrl
      ? `<img src="${esc(n.imageUrl)}" alt="${esc(n.title)}" loading="lazy" referrerpolicy="no-referrer"
              onerror="this.style.display='none';this.nextElementSibling.style.display='flex';" />
         <div class="news-media-fallback" style="display:none;">RUGBY PRO</div>`
      : `<div class="news-media-fallback">RUGBY PRO</div>`;

    const topicTxt = n.topicLabel && n.topicLabel !== 'Mundo Rugby' ? ` · ${n.topicLabel}` : '';
    return `
      <a class="news-card" href="${esc(n.url)}" target="_blank" rel="noopener noreferrer">
        <div class="news-media">${mediaHtml}</div>
        <div class="news-body">
          <div class="news-meta">${esc(n.topicLabel || 'Actualidad')} <span>· ${esc(n.dateArg || 'Hoy')}</span></div>
          <h3 class="news-title">${esc(n.title)}</h3>
          ${n.summary ? `<p class="news-summary">${esc(n.summary)}</p>` : ''}
          <span class="news-link">Leer nota completa ↗</span>
        </div>
      </a>`;
  }

  function renderLeadNewsCard(n) {
    const mediaHtml = n.imageUrl
      ? `<img src="${esc(n.imageUrl)}" alt="${esc(n.title)}" referrerpolicy="no-referrer"
              onerror="this.style.display='none';this.nextElementSibling.style.display='flex';" />
         <div class="news-media-fallback" style="display:none;">RUGBY PRO</div>`
      : `<div class="news-media-fallback">RUGBY PRO</div>`;
    return `
      <a class="news-lead" href="${esc(n.url)}" target="_blank" rel="noopener noreferrer">
        <div class="news-lead-media">${mediaHtml}</div>
        <div class="news-lead-body">
          <div>
            <div class="news-meta">${esc(n.topicLabel || 'Destacado')} <span>· ${esc(n.dateArg || 'Hoy')}</span></div>
            <h2 class="news-lead-title" style="margin-top:8px;">${esc(n.title)}</h2>
            ${n.summary ? `<p class="news-summary" style="-webkit-line-clamp:4;margin-top:10px;font-size:0.88rem;">${esc(n.summary)}</p>` : ''}
          </div>
          <div style="display:flex;align-items:center;justify-content:space-between;padding-top:10px;border-top:1px solid var(--border);">
            <span style="font-size:0.78rem;color:var(--text-dim);">Edición diaria verificada</span>
            <span style="font-size:0.84rem;font-weight:700;color:var(--gold);">Leer nota completa ↗</span>
          </div>
        </div>
      </a>`;
  }

  function renderNewsTab(d) {
    const allNews = d.news || [];
    const hasTag = (n, tag) => n.topic === tag || n.category === tag || (Array.isArray(n.tags) && n.tags.includes(tag));
    const cats = [
      { id: 'all', label: `Todas (${allNews.length})` },
      { id: 'alineaciones', label: `Alineaciones y Bajas (${allNews.filter((n) => hasTag(n, 'alineaciones')).length})` },
      { id: 'fichajes', label: `Fichajes y Mercado (${allNews.filter((n) => hasTag(n, 'fichajes')).length})` },
      { id: 'pumas', label: `Los Pumas / UAR (${allNews.filter((n) => hasTag(n, 'pumas')).length})` },
      { id: 'urba', label: `URBA Bs.As. (${allNews.filter((n) => hasTag(n, 'urba')).length})` },
      { id: 'internacional', label: `Mundo Rugby (${allNews.filter((n) => hasTag(n, 'internacional')).length})` },
    ];
    const q = norm(state.newsSearch);
    const filtered = allNews.filter((n) => {
      if (state.newsCat !== 'all' && !hasTag(n, state.newsCat)) return false;
      if (q) {
        const hay = norm(`${n.title} ${n.summary || ''} ${n.topicLabel || ''}`);
        if (!hay.includes(q)) return false;
      }
      return true;
    });

    const leadCard = filtered.length ? renderLeadNewsCard(filtered[0]) : '';
    const restCards = filtered.slice(1);

    return `
      <div class="section-head" style="margin-top:2px;">
        <h1 class="section-title">Noticias, Alineaciones y Fichajes</h1>
        <span class="section-sub">9 fuentes oficiales · Actualización automática diaria</span>
      </div>
      ${renderToolbar('newsSearchInput', state.newsSearch, 'Buscar jugador, lesión, fichaje, club, Los Pumas o torneo...')}
      <div class="filter-scroll">
        ${cats.map((c) => `<button class="seg-tab ${state.newsCat === c.id ? 'active' : ''}" data-news-cat="${c.id}" type="button">${esc(c.label)}</button>`).join('')}
      </div>
      ${filtered.length
        ? `${leadCard}${restCards.length ? `<div class="news-grid">${restCards.map(renderNewsCard).join('')}</div>` : ''}`
        : `<div class="empty-box">No hay noticias que coincidan con "${esc(state.newsSearch || state.newsCat)}".</div>`}`;
  }
  function renderTeamsTab(d) {
    const clubsUrba = (d.urba && d.urba.clubs) || [];
    const dir = d.teamsDirectory || [];
    const comps = d.competitions || [];
    const subTabs = [
      { id: 'competitions', label: `Torneos (${comps.length || 12})` },
      { id: 'all', label: `Todos (${dir.length + clubsUrba.length})` },
      { id: 'ranking', label: 'Ranking & Banderas' },
      { id: 'sra', label: 'Súper Rugby Américas (8)' },
      { id: 'top14', label: 'Top 14 Francia' },
      { id: 'world_clubs', label: 'URC · Prem · SRP · EPCR' },
      { id: 'urba', label: `URBA Bs.As. (${clubsUrba.length})` },
      { id: 'nations', label: 'Selecciones' },
      { id: 'history', label: `Historial (${(d.history || []).length})` },
    ];

    let bodyHtml = '';
    if (state.teamsSubTab === 'competitions') {
      const catOrder = ['Clubes', 'Copas', 'Selecciones'];
      bodyHtml = catOrder.map((catName) => {
        const list = comps.filter((c) => c.category === catName);
        if (!list.length) return '';
        return `
          <div class="section-head" style="margin-top:8px;">
            <h2 class="section-title" style="font-size:1.3rem;">${esc(catName === 'Copas' ? 'Copas Continentales de Clubes' : (catName === 'Clubes' ? 'Ligas Principales de Clubes' : 'Torneos de Selecciones'))}</h2>
            <span class="section-sub">${list.length} competencias oficiales</span>
          </div>
          <div class="comp-grid">
            ${list.map((c) => `
              <button class="comp-card" data-open-comp="${esc(c.id)}" type="button">
                <div>
                  <div class="comp-card-kicker ${c.isArgentinaCore ? 'arg' : ''}">${esc(c.category)} · ${esc(c.region)}</div>
                  <div class="comp-card-name" style="margin-top:4px;">${esc(c.name)}</div>
                </div>
                <div class="comp-card-foot">
                  <span><strong style="color:var(--text);">${c.teamsCount} equipos</strong></span>
                  <span aria-hidden="true">·</span>
                  <span>${esc(c.season)}</span>
                  <span aria-hidden="true">·</span>
                  <span>ID: ${esc(c.id)}</span>
                </div>
              </button>
            `).join('')}
          </div>`;
      }).join('');
    } else if (state.teamsSubTab === 'ranking') {
      const list = (d.rankings && d.rankings[state.rankGender]) || [];
      bodyHtml = `
        <div class="sub-seg">
          <button class="sub-btn ${state.rankGender === 'men' ? 'active' : ''}" data-rank-gender="men" type="button">World Rugby Masculino (${(d.rankings && d.rankings.men && d.rankings.men.length) || 0})</button>
          <button class="sub-btn ${state.rankGender === 'women' ? 'active' : ''}" data-rank-gender="women" type="button">World Rugby Femenino (${(d.rankings && d.rankings.women && d.rankings.women.length) || 0})</button>
        </div>
        <div class="table-wrap">
          <table class="rank-table">
            <thead><tr><th>Pos</th><th>Bandera y Escudo Oficial</th><th>Selección</th><th>Puntos</th><th>Var</th></tr></thead>
            <tbody>
              ${list.map((r) => {
                const diff = r.prevPos && r.pos ? r.prevPos - r.pos : 0;
                const diffTxt = diff > 0 ? `▲ +${diff}` : (diff < 0 ? `▼ ${diff}` : '—');
                const diffCol = diff > 0 ? 'var(--turf)' : (diff < 0 ? 'var(--crimson)' : 'var(--text-dim)');
                const isArg = norm(r.team) === 'argentina';
                return `
                  <tr style="${isArg ? 'background:rgba(56,189,248,0.09);' : ''}">
                    <td class="mono-num" style="font-weight:700;color:${r.pos <= 4 ? 'var(--gold)' : 'var(--text)'};">${r.pos}</td>
                    <td>
                      <div style="display:flex;align-items:center;gap:8px;">
                        ${r.flagUrl ? `<img class="flag-inline" src="${esc(r.flagUrl)}" alt="Bandera ${esc(r.team)}" loading="lazy" referrerpolicy="no-referrer" />` : ''}
                        ${renderTeamVisual(r.crestUrl, '', r.team, 'sm')}
                      </div>
                    </td>
                    <td>
                      <span style="font-weight:${isArg ? '800' : '600'};color:${isArg ? 'var(--sky)' : 'var(--text)'};">${esc(r.team)}</span>
                    </td>
                    <td class="mono-num" style="font-weight:700;">${Number(r.pts || 0).toFixed(2)}</td>
                    <td class="mono-num" style="color:${diffCol};font-weight:700;">${diffTxt}</td>
                  </tr>`;
              }).join('')}
            </tbody>
          </table>
        </div>`;
    } else if (state.teamsSubTab === 'history') {
      const hist = d.history || [];
      bodyHtml = hist.length
        ? `<div class="table-wrap">
            <table class="rank-table">
              <thead><tr><th>Fecha</th><th>Torneo</th><th>Local</th><th>Resultado</th><th>Visitante</th></tr></thead>
              <tbody>
                ${hist.map((h) => `
                  <tr>
                    <td class="mono-num" style="color:var(--text-dim);">${esc(h.dateArg)}</td>
                    <td style="color:var(--gold);font-weight:600;">${esc(h.leagueName)}</td>
                    <td style="font-weight:600;">${esc(h.homeTeam)}</td>
                    <td class="mono-num" style="font-weight:800;color:var(--text);">${esc(h.homeScore)} - ${esc(h.awayScore)}</td>
                    <td style="font-weight:600;">${esc(h.awayTeam)}</td>
                  </tr>`).join('')}
              </tbody>
            </table>
          </div>`
        : `<div class="empty-box">Sin partidos registrados en el historial todavía.</div>`;
    } else {
      const q = norm(state.teamsSearch);
      let cards = [];
      if (state.teamsSubTab === 'urba') {
        cards = clubsUrba.map((c) => ({
          type: 'urba',
          id: c.id,
          name: c.name,
          crestUrl: c.crestUrl,
          flagUrl: '',
          sub1: `URBA ${c.division}${c.standingsPos ? ` · #${c.standingsPos} (${c.pts} pts)` : ''}`,
          sub2: getUrbaClubLocation(c),
        }));
      } else if (state.teamsSubTab === 'sra') {
        cards = dir
          .filter((t) => (t.compIds || []).includes('sa-super-rugby-americas') || (t.groups || []).includes('sra'))
          .map((t) => ({
            type: 'dir',
            key: t.key,
            name: t.name,
            crestUrl: t.crestUrl,
            flagUrl: t.flagUrl || 'https://flagcdn.com/w80/ar.png',
            sub1: `Súper Rugby Américas${cleanCoach(t.coach) ? ` · DT: ${cleanCoach(t.coach)}` : ''}`,
            sub2: t.stadium || 'Franquicia oficial SRA',
          }));
      } else if (state.teamsSubTab === 'world_clubs') {
        cards = dir
          .filter((t) => (t.compIds || []).some((cid) => ['eu-urc', 'eng-premiership', 'oce-super-rugby-pacific', 'epcr-champions-cup', 'epcr-challenge-cup'].includes(cid)))
          .map((t) => ({
            type: 'dir',
            key: t.key,
            name: t.name,
            crestUrl: t.crestUrl,
            flagUrl: t.flagUrl || '',
            sub1: [(t.leagues || []).slice(0, 2).join(' · '), cleanCoach(t.coach) ? `DT: ${cleanCoach(t.coach)}` : ''].filter(Boolean).join(' · ') || 'Club Internacional',
            sub2: t.stadium || (t.roster && t.roster.length ? `${t.roster.length} jugadores verificados` : 'Escudo y ficha oficial'),
          }));
      } else if (state.teamsSubTab === 'top14') {
        cards = dir
          .filter((t) => (t.leagues || []).some((l) => /top 14/i.test(l) && !/urba/i.test(l)) || (t.groups || []).includes('top14'))
          .map((t) => ({
            type: 'dir',
            key: t.key,
            name: t.name,
            crestUrl: t.crestUrl,
            flagUrl: t.flagUrl || 'https://flagcdn.com/w80/fr.png',
            sub1: `Top 14 Francia${cleanCoach(t.coach) ? ` · DT: ${cleanCoach(t.coach)}` : ''}`,
            sub2: t.stadium || (t.roster && t.roster.length ? `${t.roster.length} jugadores verificados` : 'Club Top 14'),
          }));
      } else if (state.teamsSubTab === 'nations') {
        cards = dir
          .filter((t) => t.worldRank || t.flagUrl || (t.groups || []).some((g) => g === 'nations' || g === 'sevens' || g === 'worldcup'))
          .map((t) => ({
            type: 'dir',
            key: t.key,
            name: t.name,
            crestUrl: t.crestUrl,
            flagUrl: t.flagUrl || '',
            sub1: `${t.worldRank ? `Ranking #${t.worldRank}` : 'Selección / Franquicia'}${cleanCoach(t.coach) ? ` · DT: ${cleanCoach(t.coach)}` : ''}`,
            sub2: t.stadium || (t.roster && t.roster.length ? `${t.roster.length} jugadores en plantel` : 'Selección Oficial'),
          }));
      } else {
        const dirCards = dir.map((t) => ({
          type: 'dir',
          key: t.key,
          name: t.name,
          crestUrl: t.crestUrl,
          flagUrl: t.flagUrl || '',
          sub1: [t.worldRank ? `#${t.worldRank} Mundial` : '', cleanCoach(t.coach) ? `DT: ${cleanCoach(t.coach)}` : '', (t.leagues || [])[0] || ''].filter(Boolean).join(' · ') || 'Equipo Oficial',
          sub2: t.stadium || (t.roster && t.roster.length ? `${t.roster.length} jugadores verificados` : 'Escudo y ficha oficial'),
        }));
        const urbaMapped = clubsUrba.map((c) => ({
          type: 'urba',
          id: c.id,
          name: c.name,
          crestUrl: c.crestUrl,
          flagUrl: '',
          sub1: `URBA ${c.division}${c.standingsPos ? ` · #${c.standingsPos}` : ''}`,
          sub2: getUrbaClubLocation(c),
        }));
        const seenNames = new Set();
        cards = [...dirCards, ...urbaMapped].filter((item) => {
          const nk = norm(item.name);
          if (seenNames.has(nk)) return false;
          seenNames.add(nk);
          return true;
        });
      }

      if (q) {
        cards = cards.filter((c) => norm(`${c.name} ${c.sub1} ${c.sub2}`).includes(q));
      }

      bodyHtml = `
        ${renderToolbar('teamsSearchInput', state.teamsSearch, 'Buscar selección, club, estadio, cancha o entrenador...')}
        ${cards.length ? `
          <div class="dir-grid">
            ${cards.slice(0, 150).map((c) => `
              <button class="team-card" ${c.type === 'urba' ? `data-urba-club-id="${c.id}"` : `data-dir-key="${esc(c.key)}"`} type="button">
                ${renderTeamVisual(c.crestUrl, c.flagUrl, c.name, 'lg')}
                <div class="team-info">
                  <div class="team-name">${esc(c.name)}</div>
                  <div class="team-sub" style="color:var(--gold);">${esc(c.sub1)}</div>
                  <div class="team-sub">${esc(c.sub2)}</div>
                </div>
              </button>`).join('')}
          </div>` : `<div class="empty-box">No se encontraron clubes con "${esc(state.teamsSearch)}".</div>`}`;
    }

    return `
      <div class="section-head" style="margin-top:2px;">
        <h1 class="section-title">Clubes, Selecciones, Banderas y Ranking</h1>
        <span class="section-sub">${(d.dbStats && d.dbStats.totalLoggedMatches) || 0} partidos en BD · ${(d.dbStats && d.dbStats.totalRosters) || 0} planteles</span>
      </div>
      <div class="sub-seg">
        ${subTabs.map((s) => `<button class="sub-btn ${state.teamsSubTab === s.id ? 'active' : ''}" data-teams-sub="${s.id}" type="button">${esc(s.label)}</button>`).join('')}
      </div>
      ${bodyHtml}`;
  }

  function openSheet(htmlContent) {
    els.detailPanel.innerHTML = `<div class="sheet-handle"></div>${htmlContent}`;
    els.detailSheet.classList.remove('hidden');
  }

  function closeSheet() {
    els.detailSheet.classList.add('hidden');
  }

  function cleanCoach(val) {
    const s = String(val || '').replace(/\s*\[[^\]]*\]/g, '').trim();
    if (!s || /^(vacant|tbc|tba|none|n\/a|-)$/i.test(s)) return '';
    return s;
  }

  function openMatchSheet(matchId, forcedTab) {
    if (!state.data) return;
    let m = (state.data.matches || []).find((x) => x.id === matchId);
    if (!m && state.data.urba && state.data.urba.championships) {
      for (const ch of state.data.urba.championships) {
        m = (ch.recentMatches || []).find((x) => x.id === matchId);
        if (m) break;
      }
    }
    if (!m) return;
    if (m.source === 'wr' && !m._liveEnriched) {
      m._liveEnriched = true;
      enrichWebWrMatch(m).then(() => { if (state.sheetMatchId === matchId && !els.detailSheet.classList.contains('hidden')) openMatchSheet(matchId, state.sheetTab); });
    }

    const getTeamLineup = (tm) => {
      if (tm && tm.lineup && tm.lineup.length) return { list: tm.lineup, official: true };
      if (tm && tm.probableLineup && tm.probableLineup.length) return { list: tm.probableLineup, official: false };
      return { list: [], official: true };
    };

    const hLineupInfo = getTeamLineup(m.home);
    const aLineupInfo = getTeamLineup(m.away);
    const totalLineup = hLineupInfo.list.length + aLineupInfo.list.length;
    const totalScoring = (m.scoring && m.scoring.length) || 0;
    const totalOfficials = (m.officials && m.officials.length) || 0;

    if (forcedTab) {
      state.sheetTab = forcedTab;
    } else if (state.sheetMatchId !== matchId) {
      state.sheetTab = totalScoring > 0 ? 'summary' : (totalLineup > 0 ? 'lineups' : 'summary');
    }
    state.sheetMatchId = matchId;

    const renderLineupCol = (tm, info) => {
      const list = info.list;
      const coachTxt = cleanCoach(tm.coach);
      const badge = info.official
        ? (coachTxt ? `DT: ${coachTxt}` : (tm.worldRank ? `World Rank #${tm.worldRank}` : ''))
        : `Último XV verificado${tm.probableFromDate ? ` (${tm.probableFromDate})` : ''}`;
      return `
        <div>
          <div style="display:flex;align-items:center;gap:10px;margin-bottom:10px;">
            ${renderTeamVisual(tm.crestUrl, tm.flagUrl, tm.name, 'sm')}
            <div style="min-width:0;">
              <div style="font-weight:700;color:var(--text);">${esc(tm.name)}</div>
              ${badge ? `<div style="font-size:0.74rem;color:var(--gold);">${esc(badge)}</div>` : ''}
            </div>
          </div>
          ${list.length ? list.map((p) => `
            <div class="player-row">
              <span><span class="jersey-num">${esc(p.jersey)}</span>${esc(p.name)}${p.captain ? ' (C)' : ''}</span>
              <span style="color:var(--text-dim);font-size:0.75rem;">${esc(p.position)}${p.caps ? ` · ${p.caps}c` : ''}</span>
            </div>`).join('') : `<div style="font-size:0.82rem;color:var(--text-dim);">Sin planilla confirmada aún para este cruce.</div>`}
        </div>`;
    };

    const hasScores = m.status !== 'scheduled' && m.home.score != null && m.away.score != null;
    const scoreMain = hasScores
      ? `${m.home.score} — ${m.away.score}`
      : (m.timeArg && m.timeArg !== '00:00' ? m.timeArg : 'Programado');
    const scoreSub = hasScores
      ? (m.status === 'live' ? (m.statusText || 'EN VIVO') : (m.statusText || 'Finalizado'))
      : (m.dayArg || '');

    const hCoach = cleanCoach(m.home.coach);
    const aCoach = cleanCoach(m.away.coach);

    let tabBody = '';
    if (state.sheetTab === 'lineups') {
      tabBody = `
        <div class="sheet-section">
          <h3>Formaciones y Planteles</h3>
          <div class="lineups-split">
            ${renderLineupCol(m.home, hLineupInfo)}
            ${renderLineupCol(m.away, aLineupInfo)}
          </div>
        </div>`;
    } else if (state.sheetTab === 'info') {
      tabBody = `
        <div class="sheet-section">
          <h3>Estadio, Horario y Autoridades</h3>
          <div class="player-row"><span>Competencia</span><span style="font-weight:600;">${esc(m.leagueName)}${m.phase ? ` · ${esc(m.phase)}` : ''}</span></div>
          <div class="player-row"><span>Fecha y Hora</span><span class="mono-num" style="color:var(--gold);">${esc(m.dateArg || 'Por confirmar')}</span></div>
          ${(m.officials && m.officials.length) ? `<div class="player-row"><span>Árbitro principal</span><span>${esc((m.officials.find(o => /referee/i.test(o.role) && !/assistant|tmo|video/i.test(o.role)) || m.officials[0]).name)}</span></div>` : ''}
          <div class="player-row"><span>Estadio / Ciudad</span><span>${esc([m.venue && m.venue.name, m.venue && m.venue.city].filter(Boolean).join(', ') || 'Sede oficial')}</span></div>
        </div>
        ${totalOfficials ? `
          <div class="sheet-section">
            <h3>Árbitros y Oficiales</h3>
            ${m.officials.map((o) => `<div class="player-row"><span>${esc(o.name)}</span><span style="color:var(--text-dim);">${esc(o.role || 'Oficial')}</span></div>`).join('')}
          </div>` : ''}`;
    } else {
      tabBody = `
        ${totalScoring ? `
          <div class="sheet-section">
            <h3>Puntos y Anotadores</h3>
            ${m.scoring.map((s) => `<div class="player-row"><span><strong>${esc(s.type)}</strong> · ${esc(s.player)} (${esc(s.teamName)})</span><span class="mono-num" style="color:var(--gold);">${esc(s.minute)}</span></div>`).join('')}
          </div>` : `
          <div class="sheet-section">
            <h3>Resumen del Encuentro</h3>
            <div class="player-row"><span>Estado</span><span style="color:var(--gold);font-weight:700;">${esc(formatStatusLabel(m))}</span></div>
            <div class="player-row"><span>Sede</span><span>${esc([m.venue && m.venue.name, m.venue && m.venue.city].filter(Boolean).join(', ') || 'Sede oficial')}</span></div>
            ${hCoach || aCoach ? `<div class="player-row"><span>Head Coaches</span><span>${esc([hCoach, aCoach].filter(Boolean).join(' vs '))}</span></div>` : ''}
          </div>`}
        ${totalLineup ? `
          <div class="sheet-section">
            <h3>Formaciones Destacadas</h3>
            <div class="lineups-split">
              ${renderLineupCol(m.home, hLineupInfo)}
              ${renderLineupCol(m.away, aLineupInfo)}
            </div>
          </div>` : ''}`;
    }

    openSheet(`
      <div class="sheet-head">
        <div>
          <div style="font-size:0.76rem;font-weight:700;color:var(--gold);">${esc(m.leagueName)}${m.phase ? ` · ${esc(m.phase)}` : ''}</div>
          <h2 style="font-family:var(--font-display);font-size:1.7rem;font-weight:800;line-height:1.1;margin-top:2px;">${esc(m.home.name)} vs ${esc(m.away.name)}</h2>
          <div style="font-size:0.82rem;color:var(--text-dim);margin-top:4px;">${esc(m.dateArg || '')} · ${esc([m.venue && m.venue.name, m.venue && m.venue.city].filter(Boolean).join(', ') || 'Sede oficial')}</div>
        </div>
        <button class="action-btn" data-close-sheet="1" type="button">Cerrar</button>
      </div>
      <div class="sheet-scorebar">
        <div class="sheet-score-team home">
          ${renderTeamVisual(m.home.crestUrl, m.home.flagUrl, m.home.name, 'lg')}
          <span class="sheet-score-name">${esc(m.home.name)}</span>
        </div>
        <div class="sheet-score-center">
          <div class="sheet-score-val">${esc(scoreMain)}</div>
          ${scoreSub ? `<div class="sheet-score-sub">${esc(scoreSub)}</div>` : ''}
        </div>
        <div class="sheet-score-team away">
          ${renderTeamVisual(m.away.crestUrl, m.away.flagUrl, m.away.name, 'lg')}
          <span class="sheet-score-name">${esc(m.away.name)}</span>
        </div>
      </div>
      <div class="sub-seg" style="margin-bottom:6px;">
        <button class="sub-btn ${state.sheetTab === 'summary' ? 'active' : ''}" data-sheet-tab="summary" type="button">Resumen${totalScoring ? ` (${totalScoring})` : ''}</button>
        <button class="sub-btn ${state.sheetTab === 'lineups' ? 'active' : ''}" data-sheet-tab="lineups" type="button">Formaciones XV${totalLineup ? ` (${totalLineup})` : ''}</button>
        <button class="sub-btn ${state.sheetTab === 'info' ? 'active' : ''}" data-sheet-tab="info" type="button">Estadio &amp; Árbitros</button>
      </div>
      ${tabBody}`);
  }
  function openUrbaClubSheet(clubId) {
    if (!state.data || !state.data.urba) return;
    const club = (state.data.urba.clubs || []).find((c) => String(c.id) === String(clubId));
    if (!club) return;

    const clubMatches = [];
    for (const ch of (state.data.urba.championships || [])) {
      for (const m of (ch.recentMatches || [])) {
        if (norm(m.home.name) === norm(club.name) || norm(m.away.name) === norm(club.name)) {
          clubMatches.push(m);
        }
      }
    }
    const dirMatch = (state.data.teamsDirectory || []).find((t) => norm(t.name) === norm(club.name) || norm(t.key) === norm(club.key));

    openSheet(`
      <div class="sheet-head">
        <div style="display:flex;align-items:center;gap:12px;">
          ${renderTeamVisual(club.crestUrl, '', club.name, 'xl')}
          <div>
            <div style="font-size:0.76rem;font-weight:700;color:var(--gold);">URBA Buenos Aires · ${esc(club.division)}</div>
            <h2 style="font-family:var(--font-display);font-size:1.75rem;font-weight:800;line-height:1.1;">${esc(club.name)}</h2>
          </div>
        </div>
        <button class="action-btn" data-close-sheet="1" type="button">Cerrar</button>
      </div>
      <div class="kpi-strip" style="margin-bottom:14px;">
        <div class="kpi-cell"><div class="kpi-num" style="font-size:1.25rem;">${esc(club.division)}</div><div class="kpi-label">División 2026</div></div>
        <div class="kpi-cell"><div class="kpi-num accent" style="font-size:1.25rem;">${club.standingsPos ? `#${club.standingsPos} · ${club.pts} pts` : 'Afiliado'}</div><div class="kpi-label">${club.standingsPos ? `${club.won}G - ${club.lost}P` : 'URBA Oficial'}</div></div>
        <div class="kpi-cell"><div class="kpi-num" style="font-size:1.15rem;">${esc(club.cancha || 'Buenos Aires')}</div><div class="kpi-label">Cancha</div></div>
        <div class="kpi-cell"><div class="kpi-num" style="font-size:1.15rem;">${esc(dirMatch && cleanCoach(dirMatch.coach) ? cleanCoach(dirMatch.coach) : (club.sede || 'Buenos Aires'))}</div><div class="kpi-label">${dirMatch && cleanCoach(dirMatch.coach) ? 'Entrenador' : 'Sede Social'}</div></div>
      </div>
      ${clubMatches.length ? `
        <div class="sheet-section">
          <h3>Últimos Partidos en URBA</h3>
          <div class="cards-grid">${clubMatches.slice(0, 6).map(renderMatchCard).join('')}</div>
        </div>` : ''}`);
  }

  function openDirTeamSheet(teamKey) {
    if (!state.data) return;
    const t = (state.data.teamsDirectory || []).find((x) => x.key === teamKey);
    if (!t) return;
    const teamMatches = (state.data.matches || []).filter((m) => norm(m.home.name) === norm(t.name) || norm(m.away.name) === norm(t.name)).slice(0, 6);

    openSheet(`
      <div class="sheet-head">
        <div style="display:flex;align-items:center;gap:12px;">
          ${renderTeamVisual(t.crestUrl, t.flagUrl, t.name, 'xl')}
          <div>
            <div style="font-size:0.76rem;font-weight:700;color:var(--gold);">${esc((t.leagues || []).join(' · ') || 'Rugby Oficial')}</div>
            <h2 style="font-family:var(--font-display);font-size:1.75rem;font-weight:800;line-height:1.1;">${esc(t.name)}</h2>
          </div>
        </div>
        <button class="action-btn" data-close-sheet="1" type="button">Cerrar</button>
      </div>
      <div class="kpi-strip" style="margin-bottom:14px;">
        <div class="kpi-cell"><div class="kpi-num" style="font-size:1.15rem;">${esc(cleanCoach(t.coach) || 'Oficial')}</div><div class="kpi-label">Head Coach</div></div>
        <div class="kpi-cell"><div class="kpi-num" style="font-size:1.15rem;">${esc(t.stadium || 'Sede oficial')}</div><div class="kpi-label">Estadio</div></div>
        <div class="kpi-cell"><div class="kpi-num accent" style="font-size:1.25rem;">${t.worldRank ? `#${t.worldRank}` : 'Club'}</div><div class="kpi-label">${t.worldRank ? 'World Rugby Rank' : 'Competencia'}</div></div>
        <div class="kpi-cell"><div class="kpi-num sky" style="font-size:1.25rem;">${t.roster && t.roster.length ? t.roster.length : '—'}</div><div class="kpi-label">Jugadores en plantel</div></div>
      </div>
      ${(t.roster && t.roster.length) ? `
        <div class="sheet-section">
          <h3>Última Formación Verificada</h3>
          ${t.roster.map((p) => `<div class="player-row"><span><span class="jersey-num">${esc(p.jersey)}</span>${esc(p.name)}${p.captain ? ' (C)' : ''}</span><span style="color:var(--text-dim);font-size:0.75rem;">${esc(p.position)}</span></div>`).join('')}
        </div>` : ''}
      ${teamMatches.length ? `
        <div class="sheet-section">
          <h3>Partidos Recientes y Próximos</h3>
          <div class="cards-grid">${teamMatches.map(renderMatchCard).join('')}</div>
        </div>` : ''}`);
  }

  function render() {
    if (!state.data) return;
    const focusedId = document.activeElement && document.activeElement.id;
    let html = '';
    if (state.tab === 'matches') html = renderMatchesTab(state.data);
    else if (state.tab === 'pumas') html = renderPumasTab(state.data);
    else if (state.tab === 'urba') html = renderUrbaTab(state.data);
    else if (state.tab === 'news') html = renderNewsTab(state.data);
    else html = renderTeamsTab(state.data);

    els.main.innerHTML = html;

    if (focusedId) {
      const inp = document.getElementById(focusedId);
      if (inp && typeof inp.setSelectionRange === 'function') {
        inp.focus();
        const len = inp.value.length;
        inp.setSelectionRange(len, len);
      }
    }
  }

  document.addEventListener('click', (e) => {
    const closeBtn = e.target.closest('[data-close-sheet]');
    if (closeBtn || e.target === els.detailSheet) {
      closeSheet();
      return;
    }
    if (e.target.closest('#themeToggleBtn')) {
      toggleDaylight();
      return;
    }
    const tabLink = e.target.closest('[data-tab-link]');
    if (tabLink) {
      e.preventDefault();
      setTab(tabLink.dataset.tabLink);
      return;
    }
    const navBtn = e.target.closest('[data-tab]');
    if (navBtn) {
      setTab(navBtn.dataset.tab);
      return;
    }
    const jumpBtn = e.target.closest('[data-jump-tab]');
    if (jumpBtn) {
      setTab(jumpBtn.dataset.jumpTab);
      return;
    }
    const rkJump = e.target.closest('[data-jump-ranking]');
    if (rkJump) {
      state.teamsSubTab = 'ranking';
      setTab('teams');
      return;
    }
    const sheetTabBtn = e.target.closest('[data-sheet-tab]');
    if (sheetTabBtn && state.sheetMatchId) {
      openMatchSheet(state.sheetMatchId, sheetTabBtn.dataset.sheetTab);
      return;
    }
    const mgBtn = e.target.closest('[data-match-group]');
    if (mgBtn) {
      state.matchGroup = mgBtn.dataset.matchGroup;
      state.matchCompId = 'all';
      state.compViewMode = 'matches';
      render();
      return;
    }
    const mcBtn = e.target.closest('[data-match-comp]');
    if (mcBtn) {
      state.matchCompId = mcBtn.dataset.matchComp;
      state.compViewMode = 'matches';
      render();
      return;
    }
    const cvBtn = e.target.closest('[data-comp-view]');
    if (cvBtn) {
      state.compViewMode = cvBtn.dataset.compView;
      render();
      return;
    }
    const ocBtn = e.target.closest('[data-open-comp]');
    if (ocBtn) {
      const cid = ocBtn.dataset.openComp;
      const comps = (state.data && state.data.competitions) || [];
      const found = comps.find((x) => x.id === cid);
      state.matchCompId = cid;
      state.compViewMode = (found && found.matchesCount === 0 && found.standings && found.standings.length) ? 'standings' : 'matches';
      if (found) {
        state.matchGroup = found.category === 'Copas' ? 'cat-copas' : (found.category === 'Clubes' ? 'cat-clubes' : 'cat-selecciones');
      }
      setTab('matches');
      return;
    }
    const msBtn = e.target.closest('[data-match-status]');
    if (msBtn) {
      state.matchStatus = msBtn.dataset.matchStatus;
      render();
      return;
    }
    const ucBtn = e.target.closest('[data-urba-champ]');
    if (ucBtn) {
      state.urbaChampId = Number(ucBtn.dataset.urbaChamp);
      render();
      return;
    }
    const usBtn = e.target.closest('[data-urba-sub]');
    if (usBtn) {
      state.urbaSubTab = usBtn.dataset.urbaSub;
      render();
      return;
    }
    const ncBtn = e.target.closest('[data-news-cat]');
    if (ncBtn) {
      state.newsCat = ncBtn.dataset.newsCat;
      render();
      return;
    }
    const tsBtn = e.target.closest('[data-teams-sub]');
    if (tsBtn) {
      state.teamsSubTab = tsBtn.dataset.teamsSub;
      render();
      return;
    }
    const rgBtn = e.target.closest('[data-rank-gender]');
    if (rgBtn) {
      state.rankGender = rgBtn.dataset.rankGender;
      render();
      return;
    }
    const mCard = e.target.closest('[data-match-id]');
    if (mCard) {
      openMatchSheet(mCard.dataset.matchId, mCard.dataset.openLineup ? 'lineups' : undefined);
      return;
    }
    const uClub = e.target.closest('[data-urba-club-id]');
    if (uClub) {
      openUrbaClubSheet(uClub.dataset.urbaClubId);
      return;
    }
    const dTeam = e.target.closest('[data-dir-key]');
    if (dTeam) {
      openDirTeamSheet(dTeam.dataset.dirKey);
    }
  });

  document.addEventListener('input', (e) => {
    if (e.target.id === 'matchSearchInput') {
      state.search = e.target.value;
      render();
    } else if (e.target.id === 'urbaSearchInput') {
      state.urbaSearch = e.target.value;
      render();
    } else if (e.target.id === 'teamsSearchInput') {
      state.teamsSearch = e.target.value;
      render();
    } else if (e.target.id === 'newsSearchInput') {
      state.newsSearch = e.target.value;
      render();
    }
  });

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closeSheet();
  });

  if (els.refreshBtn) {
    els.refreshBtn.addEventListener('click', () => loadData(true));
  }

  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    state.deferredInstall = e;
    if (els.installBtn) els.installBtn.classList.remove('hidden');
  });

  if (els.installBtn) {
    els.installBtn.addEventListener('click', async () => {
      if (!state.deferredInstall) return;
      state.deferredInstall.prompt();
      await state.deferredInstall.userChoice;
      state.deferredInstall = null;
      els.installBtn.classList.add('hidden');
    });
  }

  window.addEventListener('online', () => {
    els.offlineBar.classList.add('hidden');
    loadData(true);
  });

  window.addEventListener('offline', () => {
    els.offlineBar.classList.remove('hidden');
  });

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./sw.js').catch(() => {});
  }

  loadData(false);
  setInterval(() => { overlayLiveFeeds(); }, 20000);
})();
