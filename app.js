(() => {
  'use strict';

  const DATA_URL = './data/rugby_engine_output.json';
  const FALLBACK_URL = './rugby_engine_output.json';

  const state = {
    data: null,
    tab: 'matches',
    matchGroup: 'all',
    matchStatus: 'all',
    search: '',
    urbaChampId: 2025176,
    urbaSubTab: 'standings',
    urbaSearch: '',
    newsCat: 'all',
    teamsSubTab: 'all',
    teamsSearch: '',
    rankGender: 'men',
    deferredInstall: null,
  };

  const els = {
    main: document.getElementById('appMain'),
    installBtn: document.getElementById('installBtn'),
    refreshBtn: document.getElementById('refreshBtn'),
    offlineBar: document.getElementById('offlineBar'),
    detailSheet: document.getElementById('detailSheet'),
    detailPanel: document.getElementById('detailPanel'),
    navItems: Array.from(document.querySelectorAll('.nav-item[data-tab]')),
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

  function renderCrest(url, name, sizeClass = '') {
    const safeUrl = url && !String(url).includes('25912.png') ? esc(url) : '';
    const ini = esc(initials(name));
    if (!safeUrl) {
      return `<div class="crest-frame ${sizeClass}"><span class="crest-fallback">${ini}</span></div>`;
    }
    return `
      <div class="crest-frame ${sizeClass}">
        <img src="${safeUrl}" alt="${esc(name)}" loading="lazy" referrerpolicy="no-referrer"
             onerror="this.style.display='none';this.nextElementSibling.style.display='inline-block';" />
        <span class="crest-fallback" style="display:none;">${ini}</span>
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
    const timePart = m.timeArg && m.timeArg !== '00:00' ? ` · ${m.timeArg} ART` : '';
    return `${m.dateShortArg || m.dateArg || 'Programado'}${timePart}`;
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
    const hSub = m.home.coach ? `DT: ${m.home.coach}` : (m.home.worldRank ? `Ranking #${m.home.worldRank}` : (hMeta && hMeta.division ? `URBA ${hMeta.division}` : ''));
    const aSub = m.away.coach ? `DT: ${m.away.coach}` : (m.away.worldRank ? `Ranking #${m.away.worldRank}` : (aMeta && aMeta.division ? `URBA ${aMeta.division}` : ''));

    const venueLabel = (m.venue && (m.venue.name || m.venue.city))
      ? [m.venue.name, m.venue.city].filter(Boolean).join(', ')
      : (m.phase || 'Sede oficial');

    const stateClass = isLive ? 'state-live' : (m.status === 'scheduled' ? 'state-scheduled' : 'state-finished');
    const leagueTitle = m.phase ? `${m.leagueShort || m.leagueName} · ${m.phase}` : (m.leagueShort || m.leagueName);
    const lineupReady = (m.home.lineup && m.home.lineup.length) || (m.home.probableLineup && m.home.probableLineup.length);

    return `
      <article class="match-card ${isArg ? 'is-arg' : ''} ${isLive ? 'is-live' : ''}" data-match-id="${esc(m.id)}" tabindex="0" role="button">
        <div class="card-kicker">
          <span class="kicker-league">${esc(leagueTitle)}</span>
          <span class="kicker-state ${stateClass}">${esc(formatStatusLabel(m))}</span>
        </div>
        <div class="board-rows">
          <div class="board-row">
            <div class="team-left">
              ${renderCrest(m.home.crestUrl, m.home.name)}
              <div class="team-info">
                <div class="team-name">${esc(m.home.name)}</div>
                ${hSub ? `<div class="team-sub">${esc(hSub)}</div>` : ''}
              </div>
            </div>
            <div class="row-score ${hasScores ? (hWin ? 'win' : '') : 'muted'}">
              ${hasScores ? esc(hScore) : (m.timeArg && m.timeArg !== '00:00' ? esc(m.timeArg) : 'VS')}
            </div>
          </div>
          <div class="board-row">
            <div class="team-left">
              ${renderCrest(m.away.crestUrl, m.away.name)}
              <div class="team-info">
                <div class="team-name">${esc(m.away.name)}</div>
                ${aSub ? `<div class="team-sub">${esc(aSub)}</div>` : ''}
              </div>
            </div>
            <div class="row-score ${hasScores ? (aWin ? 'win' : '') : 'muted'}">
              ${hasScores ? esc(aScore) : (m.dateShortArg ? esc(m.dateShortArg) : '-')}
            </div>
          </div>
        </div>
        <div class="card-foot">
          <span class="foot-venue">${esc(venueLabel)}</span>
          <span class="foot-cta">${lineupReady ? 'Formación y ficha ›' : 'Ver detalle ›'}</span>
        </div>
      </article>`;
  }

  function renderPumasHeroPair(d) {
    const xv = d.argentinaNextXV;
    const s7 = d.argentinaNext7s;
    const coaches = (d.argentinaHub && d.argentinaHub.coaches) || { xv: 'Felipe Contepomi', sevens: 'Santiago Gómez Cora' };

    const cardXV = xv ? `
      <section class="arg-hero" data-match-id="${esc(xv.id)}" role="button" tabindex="0">
        <div class="arg-hero-eyebrow">LOS PUMAS XV · ${esc(xv.leagueName)}</div>
        <div class="hero-matchup">
          <h2 class="arg-hero-title">${esc(xv.home.name)} vs ${esc(xv.away.name)}</h2>
          <div class="hero-crests">
            ${renderCrest(xv.home.crestUrl, xv.home.name, 'lg')}
            ${renderCrest(xv.away.crestUrl, xv.away.name, 'lg')}
          </div>
        </div>
        <div class="arg-hero-grid">
          <div>
            <div class="hero-stat-label">Fecha y Hora ARG</div>
            <div class="hero-stat-val">${esc(xv.dateArg)} · ${esc(countdownText(xv.ts))}</div>
          </div>
          <div>
            <div class="hero-stat-label">Estadio / Sede</div>
            <div class="hero-stat-val">${esc([xv.venue && xv.venue.name, xv.venue && xv.venue.city].filter(Boolean).join(', ') || 'Por confirmar')}</div>
          </div>
          <div>
            <div class="hero-stat-label">Head Coach</div>
            <div class="hero-stat-val">${esc(coaches.xv)}</div>
          </div>
          <div>
            <div class="hero-stat-label">Plantel y Ficha</div>
            <div class="hero-stat-val" style="color:var(--sky);">Tocar para ver XV y ficha ›</div>
          </div>
        </div>
      </section>` : `
      <section class="arg-hero">
        <div class="arg-hero-eyebrow">LOS PUMAS XV · UAR OFICIAL</div>
        <h2 class="arg-hero-title">Selección Argentina de Rugby XV</h2>
        <div class="arg-hero-grid">
          <div><div class="hero-stat-label">Head Coach</div><div class="hero-stat-val">${esc(coaches.xv)}</div></div>
          <div><div class="hero-stat-label">Estado</div><div class="hero-stat-val">Historial y plantel disponibles</div></div>
        </div>
      </section>`;

    const card7s = s7 ? `
      <section class="arg-hero gold-edge" data-match-id="${esc(s7.id)}" role="button" tabindex="0">
        <div class="arg-hero-eyebrow">LOS PUMAS 7s · ${esc(s7.leagueShort || s7.leagueName)}</div>
        <div class="hero-matchup">
          <h2 class="arg-hero-title">${esc(s7.home.name)} vs ${esc(s7.away.name)}</h2>
          <div class="hero-crests">
            ${renderCrest(s7.home.crestUrl, s7.home.name, 'lg')}
            ${renderCrest(s7.away.crestUrl, s7.away.name, 'lg')}
          </div>
        </div>
        <div class="arg-hero-grid">
          <div>
            <div class="hero-stat-label">Fecha y Hora ARG</div>
            <div class="hero-stat-val">${esc(s7.dateArg)} · ${esc(countdownText(s7.ts))}</div>
          </div>
          <div>
            <div class="hero-stat-label">Circuito / Sede</div>
            <div class="hero-stat-val">${esc([s7.venue && s7.venue.name, s7.venue && s7.venue.city].filter(Boolean).join(', ') || 'HSBC SVNS')}</div>
          </div>
          <div>
            <div class="hero-stat-label">Head Coach</div>
            <div class="hero-stat-val">${esc(coaches.sevens)}</div>
          </div>
          <div>
            <div class="hero-stat-label">Detalle del Partido</div>
            <div class="hero-stat-val" style="color:var(--gold);">Ver cruce y jugadores ›</div>
          </div>
        </div>
      </section>` : '';

    return `<div class="hero-pair">${cardXV}${card7s}</div>`;
  }

  function renderSummaryBar(d) {
    const sc = d.statusCounts || {};
    const gc = d.groupCounts || {};
    const liveHtml = sc.live > 0 ? `<span class="meta-live">${sc.live} en vivo</span> · ` : '';
    const urbaCount = (d.urba && d.urba.totalClubs) || 99;
    return `
      <div class="summary-bar">
        <div class="summary-meta">
          ${liveHtml}
          <span class="meta-arg">${(d.argentinaMatches || []).length} Argentina</span> ·
          <span class="meta-gold">${urbaCount} clubes URBA</span> ·
          <strong>${d.totalMatches || 0}</strong> partidos ·
          <strong>${(d.teamsDirectory || []).length + urbaCount}</strong> clubes y selecciones
        </div>
        <div class="summary-meta">Actualizado ${esc(d.generatedAtArg || '')}</div>
      </div>`;
  }

  function renderMatchesTab(d) {
    const gc = d.groupCounts || {};
    const groupTabs = [
      { id: 'all', label: `Todos (${d.totalMatches || 0})` },
      { id: 'arg', label: `Los Pumas (${(d.argentinaMatches || []).length})` },
      { id: 'urba', label: `URBA Bs.As. (${gc.urba || 0})` },
      { id: 'top14', label: `Top 14 Francia (${gc.top14 || 0})` },
      { id: 'nations', label: `Naciones (${gc.nations || 0})` },
      { id: 'sevens', label: `Seven SVNS (${gc.sevens || 0})` },
      { id: 'worldcup', label: `Mundiales (${gc.worldcup || 0})` },
      { id: 'clubs', label: `Clubes (${gc.clubs || 0})` },
    ];

    const statusTabs = [
      { id: 'all', label: 'Todos' },
      { id: 'live', label: `En Vivo (${(d.statusCounts && d.statusCounts.live) || 0})` },
      { id: 'scheduled', label: `Próximos (${(d.statusCounts && d.statusCounts.scheduled) || 0})` },
      { id: 'finished', label: `Finalizados (${(d.statusCounts && d.statusCounts.finished) || 0})` },
    ];

    const q = norm(state.search);
    const filtered = (d.matches || []).filter((m) => {
      if (state.matchGroup === 'arg' && !m.isArgentina) return false;
      if (state.matchGroup !== 'all' && state.matchGroup !== 'arg' && m.group !== state.matchGroup) return false;
      if (state.matchStatus !== 'all' && m.status !== state.matchStatus) return false;
      if (q) {
        const hay = norm(`${m.home.name} ${m.away.name} ${m.leagueName} ${m.leagueShort} ${m.phase || ''} ${m.venue && m.venue.name} ${m.venue && m.venue.city}`);
        if (!hay.includes(q)) return false;
      }
      return true;
    });

    const cardsHtml = filtered.length
      ? `<div class="cards-grid">${filtered.slice(0, 90).map(renderMatchCard).join('')}</div>`
      : `<div class="empty-box">No hay partidos que coincidan con "${esc(state.search || state.matchGroup)}". Probá cambiando el filtro arriba.</div>`;

    return `
      ${renderSummaryBar(d)}
      ${state.matchGroup === 'all' || state.matchGroup === 'arg' ? renderPumasHeroPair(d) : ''}
      <div class="search-row">
        <input id="matchSearchInput" class="search-box" type="search" placeholder="Buscar equipo, torneo, URBA, Top 14 o estadio..." value="${esc(state.search)}" />
      </div>
      <div class="filter-scroll">
        ${groupTabs.map((t) => `<button class="seg-tab ${state.matchGroup === t.id ? 'active' : ''}" data-match-group="${t.id}" type="button">${esc(t.label)}</button>`).join('')}
      </div>
      <div class="sub-seg">
        ${statusTabs.map((s) => `<button class="sub-btn ${state.matchStatus === s.id ? 'active' : ''}" data-match-status="${s.id}" type="button">${esc(s.label)}</button>`).join('')}
      </div>
      ${cardsHtml}`;
  }

  function renderPumasTab(d) {
    const hub = d.argentinaHub || {};
    const roster = hub.xvRoster || [];
    const tournaments = hub.tournaments || [];
    const argMatches = d.argentinaMatches || [];
    const pumasNews = (d.news || []).filter((n) => n.category === 'pumas' || /puma|uar|contepomi|g[oó]mez cora|argentina/i.test(n.title)).slice(0, 6);

    const starters = roster.filter((p) => p.starter);
    const bench = roster.filter((p) => !p.starter);

    const tournamentsHtml = tournaments.map((t) => `
      <div class="team-card" style="cursor:default;flex-direction:column;align-items:flex-start;gap:4px;">
        <div style="font-size:0.72rem;font-weight:700;color:var(--sky);">${esc(t.team)} · ${esc(t.coach)}</div>
        <div style="font-family:var(--font-display);font-size:1.15rem;font-weight:800;color:var(--text);">${esc(t.name)}</div>
        <div style="font-size:0.8rem;color:var(--text-soft);">${esc(t.status)}</div>
      </div>`).join('');

    const rosterHtml = roster.length ? `
      <div class="surface-box" style="padding:14px;">
        <div class="lineups-split">
          <div>
            <div style="font-size:0.78rem;font-weight:700;color:var(--gold);margin-bottom:6px;">XV Titular — Última formación oficial</div>
            ${starters.map((p) => `
              <div class="player-row">
                <span><span class="jersey-num">${esc(p.jersey)}</span>${esc(p.name)}${p.captain ? ' (C)' : ''}</span>
                <span style="color:var(--text-dim);font-size:0.74rem;">${esc(p.position)}</span>
              </div>`).join('')}
          </div>
          <div>
            <div style="font-size:0.78rem;font-weight:700;color:var(--sky);margin-bottom:6px;">Suplentes e Impacto</div>
            ${(bench.length ? bench : []).map((p) => `
              <div class="player-row">
                <span><span class="jersey-num">${esc(p.jersey)}</span>${esc(p.name)}</span>
                <span style="color:var(--text-dim);font-size:0.74rem;">${esc(p.position)}</span>
              </div>`).join('') || '<div class="empty-box">Banco por confirmar</div>'}
          </div>
        </div>
      </div>` : `<div class="empty-box">Alineación de Los Pumas se actualiza al confirmarse el próximo Test Match.</div>`;

    const newsHtml = pumasNews.length
      ? `<div class="news-grid">${pumasNews.map(renderNewsCard).join('')}</div>`
      : '';

    return `
      ${renderSummaryBar(d)}
      ${renderPumasHeroPair(d)}
      <div class="section-head">
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
        <h2 class="section-title">Último Plantel Oficial — Los Pumas XV</h2>
        <span class="section-sub">Coach: ${esc((hub.coaches && hub.coaches.xv) || 'Felipe Contepomi')}</span>
      </div>
      ${rosterHtml}

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
        <tr data-urba-club-id="${r.clubId}" style="cursor:pointer;">
          <td class="mono-num" style="color:${r.pos <= 4 ? 'var(--gold)' : 'var(--text-dim)'};font-weight:700;">${r.pos}</td>
          <td>
            <div style="display:flex;align-items:center;gap:8px;min-width:140px;">
              ${renderCrest(r.crestUrl, r.name, 'sm')}
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
          <span class="section-sub">${activeChamp.totalRounds || 0} fechas · Tocá un club para ver cancha</span>
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
        <div class="search-row">
          <input id="urbaSearchInput" class="search-box" type="search" placeholder="Buscar club de Buenos Aires, división, sede o cancha..." value="${esc(state.urbaSearch)}" />
        </div>
        <div class="dir-grid">
          ${filteredClubs.map((c) => `
            <button class="team-card" data-urba-club-id="${c.id}" type="button">
              ${renderCrest(c.crestUrl, c.name, 'lg')}
              <div class="team-info">
                <div class="team-name">${esc(c.name)}</div>
                <div class="team-sub" style="color:var(--gold);">${esc(c.division)}${c.standingsPos ? ` · #${c.standingsPos} (${c.pts} pts)` : ''}</div>
                <div class="team-sub">${esc(getUrbaClubLocation(c))}</div>
              </div>
            </button>`).join('')}
        </div>`;
    }

    return `
      ${renderSummaryBar(d)}
      <div class="arg-hero gold-edge" style="margin-bottom:14px;">
        <div class="arg-hero-eyebrow">UNIÓN DE RUGBY DE BUENOS AIRES · OFICIAL</div>
        <h2 class="arg-hero-title">Todos los Campeonatos y Clubes de Buenos Aires</h2>
        <div style="font-size:0.82rem;color:var(--text-soft);">Top 14, Primera A, B, C, Segunda, Tercera, Desarrollo y Femenino · ${clubs.length} clubes afiliados con escudos y canchas oficiales.</div>
      </div>
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

    return `
      <a class="news-card" href="${esc(n.url)}" target="_blank" rel="noopener noreferrer">
        <div class="news-media">${mediaHtml}</div>
        <div class="news-body">
          <div class="news-meta">${esc(n.source)} <span>· ${esc(n.dateArg || 'Hoy')}</span></div>
          <h3 class="news-title">${esc(n.title)}</h3>
          ${n.summary ? `<p class="news-summary">${esc(n.summary)}</p>` : ''}
          <span class="news-link">Leer nota completa ↗</span>
        </div>
      </a>`;
  }

  function renderNewsTab(d) {
    const allNews = d.news || [];
    const cats = [
      { id: 'all', label: `Todas (${allNews.length})` },
      { id: 'pumas', label: `Los Pumas / UAR (${allNews.filter((n) => n.category === 'pumas').length})` },
      { id: 'urba', label: `URBA Buenos Aires (${allNews.filter((n) => n.category === 'urba').length})` },
      { id: 'internacional', label: `Internacional (${allNews.filter((n) => n.category === 'internacional').length})` },
    ];
    const filtered = allNews.filter((n) => state.newsCat === 'all' || n.category === state.newsCat);

    return `
      ${renderSummaryBar(d)}
      <div class="section-head" style="margin-top:0;">
        <h2 class="section-title">Últimas Noticias de Rugby</h2>
        <span class="section-sub">UAR · URBA · Córdoba XV · BBC Rugby</span>
      </div>
      <div class="filter-scroll">
        ${cats.map((c) => `<button class="seg-tab ${state.newsCat === c.id ? 'active' : ''}" data-news-cat="${c.id}" type="button">${esc(c.label)}</button>`).join('')}
      </div>
      ${filtered.length ? `<div class="news-grid">${filtered.map(renderNewsCard).join('')}</div>` : `<div class="empty-box">No hay noticias en esta categoría.</div>`}`;
  }

  function renderTeamsTab(d) {
    const clubsUrba = (d.urba && d.urba.clubs) || [];
    const dir = d.teamsDirectory || [];
    const subTabs = [
      { id: 'all', label: `Todos (${dir.length + clubsUrba.length})` },
      { id: 'top14', label: 'Top 14 Francia' },
      { id: 'urba', label: `URBA Bs.As. (${clubsUrba.length})` },
      { id: 'nations', label: 'Selecciones' },
      { id: 'ranking', label: 'Ranking Mundial' },
      { id: 'history', label: `Historial (${(d.history || []).length})` },
    ];

    let bodyHtml = '';
    if (state.teamsSubTab === 'ranking') {
      const list = (d.rankings && d.rankings[state.rankGender]) || [];
      bodyHtml = `
        <div class="sub-seg">
          <button class="sub-btn ${state.rankGender === 'men' ? 'active' : ''}" data-rank-gender="men" type="button">World Rugby Masculino</button>
          <button class="sub-btn ${state.rankGender === 'women' ? 'active' : ''}" data-rank-gender="women" type="button">World Rugby Femenino</button>
        </div>
        <div class="table-wrap">
          <table class="rank-table">
            <thead><tr><th>Pos</th><th>Selección</th><th>Puntos</th><th>Mov</th></tr></thead>
            <tbody>
              ${list.map((r) => {
                const diff = r.prevPos && r.pos ? r.prevPos - r.pos : 0;
                const diffTxt = diff > 0 ? `+${diff}` : (diff < 0 ? `${diff}` : '—');
                const diffCol = diff > 0 ? 'var(--emerald)' : (diff < 0 ? 'var(--crimson)' : 'var(--text-dim)');
                const isArg = norm(r.team) === 'argentina';
                return `
                  <tr style="${isArg ? 'background:rgba(56,189,248,0.08);' : ''}">
                    <td class="mono-num" style="font-weight:700;color:${r.pos <= 4 ? 'var(--gold)' : 'var(--text)'};">${r.pos}</td>
                    <td>
                      <div style="display:flex;align-items:center;gap:8px;">
                        ${renderCrest(r.crestUrl, r.team, 'sm')}
                        <span style="font-weight:${isArg ? '800' : '600'};color:${isArg ? 'var(--sky)' : 'var(--text)'};">${esc(r.team)}</span>
                      </div>
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
              <thead><tr><th>Fecha</th><th>Torneo</th><th>Local</th><th>Res</th><th>Visitante</th></tr></thead>
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
          sub1: `URBA ${c.division}${c.standingsPos ? ` · #${c.standingsPos} (${c.pts} pts)` : ''}`,
          sub2: getUrbaClubLocation(c),
        }));
      } else if (state.teamsSubTab === 'top14') {
        cards = dir
          .filter((t) => (t.leagues || []).includes('Top 14') || (t.groups || []).includes('top14') && /^[A-Z]/.test(t.name || ''))
          .map((t) => ({
            type: 'dir',
            key: t.key,
            name: t.name,
            crestUrl: t.crestUrl,
            sub1: `Top 14 Francia${t.coach ? ` · DT: ${t.coach}` : ''}`,
            sub2: t.stadium || (t.roster && t.roster.length ? `${t.roster.length} jugadores verificados` : 'Club Top 14'),
          }));
      } else if (state.teamsSubTab === 'nations') {
        cards = dir
          .filter((t) => t.worldRank || (t.groups || []).some((g) => g === 'nations' || g === 'sevens' || g === 'worldcup'))
          .map((t) => ({
            type: 'dir',
            key: t.key,
            name: t.name,
            crestUrl: t.crestUrl,
            sub1: `${t.worldRank ? `Ranking #${t.worldRank}` : 'Selección'}${t.coach ? ` · DT: ${t.coach}` : ''}`,
            sub2: t.stadium || (t.roster && t.roster.length ? `${t.roster.length} jugadores en plantel` : 'Selección Oficial'),
          }));
      } else {
        const dirCards = dir.map((t) => ({
          type: 'dir',
          key: t.key,
          name: t.name,
          crestUrl: t.crestUrl,
          sub1: [t.worldRank ? `#${t.worldRank} Mundial` : '', t.coach ? `DT: ${t.coach}` : '', (t.leagues || [])[0] || ''].filter(Boolean).join(' · ') || 'Equipo Oficial',
          sub2: t.stadium || (t.roster && t.roster.length ? `${t.roster.length} jugadores verificados` : 'Escudo y ficha oficial'),
        }));
        const urbaMapped = clubsUrba.map((c) => ({
          type: 'urba',
          id: c.id,
          name: c.name,
          crestUrl: c.crestUrl,
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
        <div class="search-row">
          <input id="teamsSearchInput" class="search-box" type="search" placeholder="Buscar club, selección, estadio, cancha o entrenador..." value="${esc(state.teamsSearch)}" />
        </div>
        ${cards.length ? `
          <div class="dir-grid">
            ${cards.slice(0, 120).map((c) => `
              <button class="team-card" ${c.type === 'urba' ? `data-urba-club-id="${c.id}"` : `data-dir-key="${esc(c.key)}"`} type="button">
                ${renderCrest(c.crestUrl, c.name, 'lg')}
                <div class="team-info">
                  <div class="team-name">${esc(c.name)}</div>
                  <div class="team-sub" style="color:var(--gold);">${esc(c.sub1)}</div>
                  <div class="team-sub">${esc(c.sub2)}</div>
                </div>
              </button>`).join('')}
          </div>` : `<div class="empty-box">No se encontraron clubes con "${esc(state.teamsSearch)}".</div>`}`;
    }

    return `
      ${renderSummaryBar(d)}
      <div class="section-head" style="margin-top:0;">
        <h2 class="section-title">Clubes, Selecciones, Ranking e Historial</h2>
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

  function openMatchSheet(matchId) {
    if (!state.data) return;
    let m = (state.data.matches || []).find((x) => x.id === matchId);
    if (!m && state.data.urba && state.data.urba.championships) {
      for (const ch of state.data.urba.championships) {
        m = (ch.recentMatches || []).find((x) => x.id === matchId);
        if (m) break;
      }
    }
    if (!m) return;

    const renderLineupCol = (tm) => {
      const list = (tm.lineup && tm.lineup.length) ? tm.lineup : (tm.probableLineup || []);
      const badge = (tm.lineup && tm.lineup.length)
        ? 'Formación Oficial'
        : (tm.probableLineup && tm.probableLineup.length ? `Última formación (${tm.probableFromDate || 'reciente'})` : '');
      return `
        <div>
          <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px;">
            ${renderCrest(tm.crestUrl, tm.name, 'sm')}
            <div>
              <div style="font-weight:700;color:var(--text);">${esc(tm.name)}</div>
              ${badge ? `<div style="font-size:0.72rem;color:var(--gold);">${esc(badge)}</div>` : ''}
            </div>
          </div>
          ${list.length ? list.map((p) => `
            <div class="player-row">
              <span><span class="jersey-num">${esc(p.jersey)}</span>${esc(p.name)}${p.captain ? ' (C)' : ''}</span>
              <span style="color:var(--text-dim);font-size:0.74rem;">${esc(p.position)}</span>
            </div>`).join('') : `<div style="font-size:0.8rem;color:var(--text-dim);">Sin planilla confirmada aún para este cruce.</div>`}
        </div>`;
    };

    const hasScores = m.status !== 'scheduled' && m.home.score != null && m.away.score != null;
    const scoreStr = hasScores ? `${m.home.score} — ${m.away.score}` : (m.timeArg && m.timeArg !== '00:00' ? `${m.timeArg} ART` : 'Programado');

    openSheet(`
      <div class="sheet-head">
        <div>
          <div style="font-size:0.74rem;font-weight:700;color:var(--gold);">${esc(m.leagueName)}${m.phase ? ` · ${esc(m.phase)}` : ''}</div>
          <h2 style="font-family:var(--font-display);font-size:1.55rem;font-weight:800;line-height:1.15;margin-top:2px;">${esc(m.home.name)} vs ${esc(m.away.name)}</h2>
          <div style="font-size:0.8rem;color:var(--text-dim);margin-top:4px;">${esc(m.dateArg || '')} · ${esc([m.venue && m.venue.name, m.venue && m.venue.city].filter(Boolean).join(', ') || 'Sede oficial')}</div>
        </div>
        <button class="action-btn" data-close-sheet="1" type="button">Cerrar</button>
      </div>
      <div style="display:flex;align-items:center;justify-content:space-between;background:var(--surface-2);border:1px solid var(--border);border-radius:14px;padding:12px 14px;margin-bottom:8px;">
        <div style="display:flex;align-items:center;gap:10px;">${renderCrest(m.home.crestUrl, m.home.name, 'lg')}<span style="font-weight:700;">${esc(m.home.name)}</span></div>
        <div style="font-family:var(--font-display);font-size:1.65rem;font-weight:800;color:var(--gold);font-variant-numeric:tabular-nums;">${esc(scoreStr)}</div>
        <div style="display:flex;align-items:center;gap:10px;"><span style="font-weight:700;text-align:right;">${esc(m.away.name)}</span>${renderCrest(m.away.crestUrl, m.away.name, 'lg')}</div>
      </div>
      ${(m.scoring && m.scoring.length) ? `
        <div class="sheet-section">
          <h3>Puntos y Anotadores</h3>
          ${m.scoring.map((s) => `<div class="player-row"><span><strong>${esc(s.type)}</strong> · ${esc(s.player)} (${esc(s.teamName)})</span><span class="mono-num" style="color:var(--gold);">${esc(s.minute)}</span></div>`).join('')}
        </div>` : ''}
      <div class="sheet-section">
        <h3>Formaciones y Planteles</h3>
        <div class="lineups-split">
          ${renderLineupCol(m.home)}
          ${renderLineupCol(m.away)}
        </div>
      </div>
      ${(m.officials && m.officials.length) ? `
        <div class="sheet-section">
          <h3>Árbitros y Oficiales</h3>
          ${m.officials.map((o) => `<div class="player-row"><span>${esc(o.name)}</span><span style="color:var(--text-dim);">${esc(o.role)} ${o.country ? `(${esc(o.country)})` : ''}</span></div>`).join('')}
        </div>` : ''}`);
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
          ${renderCrest(club.crestUrl, club.name, 'lg')}
          <div>
            <div style="font-size:0.74rem;font-weight:700;color:var(--gold);">URBA Buenos Aires · ${esc(club.division)}</div>
            <h2 style="font-family:var(--font-display);font-size:1.55rem;font-weight:800;line-height:1.15;">${esc(club.name)}</h2>
          </div>
        </div>
        <button class="action-btn" data-close-sheet="1" type="button">Cerrar</button>
      </div>
      <div class="arg-hero-grid" style="border-top:1px solid var(--border);padding-top:12px;margin-bottom:12px;">
        <div><div class="hero-stat-label">División 2026</div><div class="hero-stat-val">${esc(club.division)}</div></div>
        <div><div class="hero-stat-label">Posición y Puntos</div><div class="hero-stat-val">${club.standingsPos ? `#${club.standingsPos} · ${club.pts} pts (${club.won}G-${club.lost}P)` : 'Afiliado URBA'}</div></div>
        <div><div class="hero-stat-label">Cancha</div><div class="hero-stat-val">${esc(club.cancha || 'Buenos Aires')}</div></div>
        <div><div class="hero-stat-label">Sede Social</div><div class="hero-stat-val">${esc(club.sede || 'Buenos Aires')}</div></div>
        ${dirMatch && dirMatch.coach ? `<div><div class="hero-stat-label">Entrenador</div><div class="hero-stat-val">${esc(dirMatch.coach)}</div></div>` : ''}
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
          ${renderCrest(t.crestUrl, t.name, 'lg')}
          <div>
            <div style="font-size:0.74rem;font-weight:700;color:var(--gold);">${esc((t.leagues || []).join(' · ') || 'Rugby Oficial')}</div>
            <h2 style="font-family:var(--font-display);font-size:1.55rem;font-weight:800;line-height:1.15;">${esc(t.name)}</h2>
          </div>
        </div>
        <button class="action-btn" data-close-sheet="1" type="button">Cerrar</button>
      </div>
      <div class="arg-hero-grid" style="border-top:1px solid var(--border);padding-top:12px;margin-bottom:12px;">
        <div><div class="hero-stat-label">Head Coach</div><div class="hero-stat-val">${esc(t.coach || 'Oficial')}</div></div>
        <div><div class="hero-stat-label">Estadio</div><div class="hero-stat-val">${esc(t.stadium || 'Sede oficial')}</div></div>
        <div><div class="hero-stat-label">Ranking Mundial</div><div class="hero-stat-val">${t.worldRank ? `#${t.worldRank} World Rugby` : 'Competencia de Clubes'}</div></div>
        <div><div class="hero-stat-label">Plantel Verificado</div><div class="hero-stat-val">${t.roster && t.roster.length ? `${t.roster.length} jugadores (${t.rosterUpdated})` : 'Se actualiza en fecha'}</div></div>
      </div>
      ${(t.roster && t.roster.length) ? `
        <div class="sheet-section">
          <h3>Última Formación Verificada</h3>
          ${t.roster.map((p) => `<div class="player-row"><span><span class="jersey-num">${esc(p.jersey)}</span>${esc(p.name)}${p.captain ? ' (C)' : ''}</span><span style="color:var(--text-dim);font-size:0.74rem;">${esc(p.position)}</span></div>`).join('')}
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

  // Unified Event Delegation (Zero fragile inline handlers)
  document.addEventListener('click', (e) => {
    const closeBtn = e.target.closest('[data-close-sheet]');
    if (closeBtn || e.target === els.detailSheet) {
      closeSheet();
      return;
    }

    const navBtn = e.target.closest('.nav-item[data-tab]');
    if (navBtn) {
      setTab(navBtn.dataset.tab);
      return;
    }

    const jumpBtn = e.target.closest('[data-jump-tab]');
    if (jumpBtn) {
      setTab(jumpBtn.dataset.jumpTab);
      return;
    }

    const mgBtn = e.target.closest('[data-match-group]');
    if (mgBtn) {
      state.matchGroup = mgBtn.dataset.matchGroup;
      render();
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
      openMatchSheet(mCard.dataset.matchId);
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
})();
