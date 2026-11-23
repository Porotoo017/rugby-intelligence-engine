(() => {
  'use strict';

  const state = {
    data: null,
    tab: 'matches',
    group: 'all',
    statusFilter: 'all',
    search: '',
    rankMode: 'men',
    deferredPrompt: null,
    isInstalled: false,
  };

  const els = {
    main: document.getElementById('appMain'),
    sheet: document.getElementById('detailSheet'),
    panel: document.getElementById('detailPanel'),
    refreshBtn: document.getElementById('refreshBtn'),
    installBtn: document.getElementById('installBtn'),
    offlineBar: document.getElementById('offlineBar'),
    navItems: Array.from(document.querySelectorAll('.nav-item')),
  };

  function esc(str) {
    return String(str ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function initials(name) {
    const parts = String(name || 'RG').trim().split(/\s+/);
    if (parts.length === 1) return parts[0].slice(0, 3).toUpperCase();
    return (parts[0][0] + (parts[1][0] || '')).toUpperCase();
  }

  function renderCrest(team, sizePx = 44) {
    const abbr = esc(team.abbr || initials(team.name));
    if (team.crestUrl) {
      return `<div class="crest-wrap" style="width:${sizePx}px;height:${sizePx}px">
        <img src="${esc(team.crestUrl)}" alt="${esc(team.name)}" referrerpolicy="no-referrer" loading="lazy"
          onerror="this.parentElement.innerHTML='<span class=\\'crest-fallback\\'>${abbr}</span>'" />
      </div>`;
    }
    return `<div class="crest-wrap" style="width:${sizePx}px;height:${sizePx}px"><span class="crest-fallback">${abbr}</span></div>`;
  }

  function stateLabel(m) {
    if (m.status === 'live') return { cls: 'state-live', txt: `EN VIVO · ${esc(m.statusText || 'En juego')}` };
    if (m.status === 'finished') return { cls: 'state-finished', txt: `${esc(m.dayArg || 'Finalizado')} · Final` };
    return { cls: 'state-scheduled', txt: esc(m.dateArg || 'Programado') };
  }

  function formatCountdown(ts) {
    if (!ts) return 'Por confirmar';
    const diff = ts * 1000 - Date.now();
    if (diff <= 0) return 'En juego o finalizado';
    const d = Math.floor(diff / 86400000);
    const h = Math.floor((diff % 86400000) / 3600000);
    const min = Math.floor((diff % 3600000) / 60000);
    if (d > 0) return `Faltan ${d}d ${h}h ${min}m`;
    return `Faltan ${h}h ${min}m`;
  }

  function renderMatchCard(m) {
    const st = stateLabel(m);
    const venueParts = [m.venue?.name, m.venue?.city].filter(Boolean).join(' · ');
    const isLive = m.status === 'live';
    const showScore = m.status !== 'scheduled' && m.home.score !== null && m.away.score !== null;
    const hasLineup = (m.home.lineup?.length || 0) > 0 || (m.away.lineup?.length || 0) > 0;
    const phaseTxt = m.phase ? ` · ${m.phase}` : '';

    return `<button type="button" class="match-card ${m.isArgentina ? 'is-arg' : ''} ${isLive ? 'is-live' : ''}" data-match-id="${esc(m.id)}">
      <div class="card-kicker">
        <span class="kicker-league">${esc(m.leagueName)}${esc(phaseTxt)}</span>
        <span class="kicker-state ${st.cls}">${st.txt}</span>
      </div>

      <div class="faceoff">
        <div class="team-col">
          ${renderCrest(m.home, 44)}
          <div class="team-info">
            <div class="team-name">${esc(m.home.name)}</div>
            <div class="team-sub">${m.home.coach ? `DT: ${esc(m.home.coach)}` : (m.home.worldRank ? `Ranking #${m.home.worldRank}` : 'Local')}</div>
          </div>
        </div>

        <div class="score-center">
          ${showScore
            ? `<div class="score-num">${esc(m.home.score)} - ${esc(m.away.score)}</div>`
            : `<div class="score-vs">VS</div>`}
          <div class="score-time">${esc(m.timeArg || '')}</div>
        </div>

        <div class="team-col away">
          ${renderCrest(m.away, 44)}
          <div class="team-info">
            <div class="team-name">${esc(m.away.name)}</div>
            <div class="team-sub">${m.away.coach ? `DT: ${esc(m.away.coach)}` : (m.away.worldRank ? `Ranking #${m.away.worldRank}` : 'Visitante')}</div>
          </div>
        </div>
      </div>

      <div class="card-foot">
        <span class="foot-venue">${venueParts ? esc(venueParts) : 'Estadio por confirmar'}</span>
        <span class="foot-cta">${hasLineup ? 'Ver XV y detalles ›' : 'Detalles y plantel ›'}</span>
      </div>
    </button>`;
  }

  function renderMatchesTab() {
    const matches = state.data?.matches || [];
    const sc = state.data?.statusCounts || { live: 0, scheduled: 0, finished: 0 };
    const gc = state.data?.groupCounts || {};
    const argCount = (state.data?.argentinaMatches || []).length;

    const q = state.search.trim().toLowerCase();
    const filtered = matches.filter((m) => {
      if (state.group === 'argentina' && !m.isArgentina) return false;
      if (state.group !== 'all' && state.group !== 'argentina' && m.group !== state.group) return false;
      if (state.statusFilter !== 'all' && m.status !== state.statusFilter) return false;
      if (q) {
        const hay = `${m.home.name} ${m.away.name} ${m.leagueName} ${m.venue?.name || ''} ${m.venue?.city || ''} ${m.home.coach || ''} ${m.away.coach || ''}`.toLowerCase();
        if (!hay.includes(q)) return false;
      }
      return true;
    });

    const groups = [
      { id: 'all', label: `Todos (${matches.length})` },
      { id: 'argentina', label: `Argentina (${argCount})` },
      { id: 'nations', label: `Naciones (${gc.nations || 0})` },
      { id: 'sevens', label: `Seven SVNS (${gc.sevens || 0})` },
      { id: 'worldcup', label: `Mundiales (${gc.worldcup || 0})` },
      { id: 'clubs', label: `Clubes (${(gc.clubs || 0) + (gc.arg || 0)})` },
    ];

    const statusBtns = [
      { id: 'all', label: `Todos (${matches.length})` },
      { id: 'live', label: `En Vivo (${sc.live || 0})` },
      { id: 'scheduled', label: `Próximos (${sc.scheduled || 0})` },
      { id: 'finished', label: `Finalizados (${sc.finished || 0})` },
    ];

    return `
      <div class="summary-bar">
        <div class="summary-meta">
          <span class="meta-live">${sc.live || 0} en vivo</span>
          <span aria-hidden="true">·</span>
          <span><strong>${sc.scheduled || 0}</strong> próximos</span>
          <span aria-hidden="true">·</span>
          <span><strong>${sc.finished || 0}</strong> finalizados</span>
          <span aria-hidden="true">·</span>
          <span>Actualizado ${esc(state.data?.generatedAtArg || '')}</span>
        </div>
      </div>

      <div class="search-row">
        <input id="searchInput" class="search-box" type="search" value="${esc(state.search)}" placeholder="Buscar equipo, torneo, estadio o entrenador..." />
      </div>

      <div class="filter-scroll" role="tablist" aria-label="Torneos">
        ${groups.map((g) => `<button type="button" class="seg-tab ${state.group === g.id ? 'active' : ''}" data-group="${g.id}">${esc(g.label)}</button>`).join('')}
      </div>

      <div class="sub-seg" role="tablist" aria-label="Estado del partido">
        ${statusBtns.map((s) => `<button type="button" class="sub-btn ${state.statusFilter === s.id ? 'active' : ''}" data-status="${s.id}">${esc(s.label)}</button>`).join('')}
      </div>

      ${filtered.length
        ? `<div class="cards-grid">${filtered.map(renderMatchCard).join('')}</div>`
        : `<div class="empty-box">No hay partidos que coincidan con el filtro actual.</div>`}
    `;
  }

  function renderHeroSpotlight(item, eyebrow, isGold = false) {
    if (!item) return '';
    return `
      <section class="arg-hero ${isGold ? 'gold-edge' : ''}">
        <div class="arg-hero-eyebrow">${esc(eyebrow)} · ${esc(item.leagueName)}</div>
        <h2 class="arg-hero-title">${esc(item.home.name)} vs ${esc(item.away.name)}</h2>
        <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin:10px 0;">
          ${renderCrest(item.home, 48)}
          <span style="font-family:var(--font-display);font-size:1.4rem;font-weight:800;color:var(--gold);">VS</span>
          ${renderCrest(item.away, 48)}
          <button type="button" class="action-btn primary" data-match-id="${esc(item.id)}" style="margin-left:auto;">Ver Ficha</button>
        </div>
        <div class="arg-hero-grid">
          <div>
            <div class="hero-stat-label">Fecha y Hora (ART)</div>
            <div class="hero-stat-val">${esc(item.dateArg || 'Por confirmar')}</div>
          </div>
          <div>
            <div class="hero-stat-label">Cuenta Regresiva</div>
            <div class="hero-stat-val" style="color:var(--emerald);">${esc(formatCountdown(item.ts))}</div>
          </div>
          <div>
            <div class="hero-stat-label">Estadio y Ciudad</div>
            <div class="hero-stat-val">${esc([item.venue?.name, item.venue?.city].filter(Boolean).join(' · ') || 'Por confirmar')}</div>
          </div>
          <div>
            <div class="hero-stat-label">Entrenadores</div>
            <div class="hero-stat-val">${esc([item.home.coach, item.away.coach].filter(Boolean).join(' / ') || 'Oficiales')}</div>
          </div>
        </div>
      </section>
    `;
  }

  function renderArgentinaTab() {
    const xv = state.data?.argentinaNextXV || state.data?.argentinaNext;
    const sv = state.data?.argentinaNext7s;
    const list = state.data?.argentinaMatches || [];

    const hasHero = xv || sv;
    const heroPair = hasHero
      ? `<div class="hero-pair">
          ${renderHeroSpotlight(xv, 'PRÓXIMO LOS PUMAS XV', false)}
          ${renderHeroSpotlight(sv, 'PRÓXIMO LOS PUMAS 7s', true)}
        </div>`
      : '';

    return `
      ${heroPair}
      <div class="summary-bar">
        <div class="summary-meta">
          <span class="meta-arg">Agenda Argentina (${list.length} encuentros)</span>
          <span aria-hidden="true">·</span>
          <span>Los Pumas XV · Los Pumas 7s SVNS · URBA Top 14</span>
        </div>
      </div>
      ${list.length
        ? `<div class="cards-grid">${list.map(renderMatchCard).join('')}</div>`
        : `<div class="empty-box">No se encontraron partidos de Argentina en la ventana actual.</div>`}
    `;
  }

  function renderTeamsTab() {
    const dir = state.data?.teamsDirectory || [];
    const q = state.search.trim().toLowerCase();
    const filtered = dir.filter((t) => {
      if (!q) return true;
      return `${t.name} ${t.coach} ${t.stadium}`.toLowerCase().includes(q);
    });

    return `
      <div class="summary-bar">
        <div class="summary-meta">
          <strong>${dir.length}</strong> clubes y selecciones verificadas
          <span aria-hidden="true">·</span>
          <span>Escudos oficiales, entrenadores y último plantel</span>
        </div>
      </div>
      <div class="search-row">
        <input id="searchInput" class="search-box" type="search" value="${esc(state.search)}" placeholder="Buscar selección, club URBA, Top 14 o entrenador..." />
      </div>
      <div class="dir-grid">
        ${filtered.slice(0, 64).map((t) => `
          <button type="button" class="team-card" data-team-key="${esc(t.key)}">
            ${renderCrest(t, 46)}
            <div class="team-info" style="flex:1;">
              <div class="team-name">${esc(t.name)} ${t.worldRank ? `<span style="color:var(--gold);font-family:var(--font-mono);font-size:0.78rem;">#${t.worldRank}</span>` : ''}</div>
              <div class="team-sub">${t.coach ? `DT: ${esc(t.coach)}` : 'Entrenador oficial'}${t.stadium ? ` · ${esc(t.stadium)}` : ''}</div>
              <div class="team-sub" style="color:var(--emerald);">${t.roster?.length ? `${t.roster.length} jugadores en plantel (${esc(t.rosterUpdated)})` : 'Escudo oficial verificado'}</div>
            </div>
          </button>
        `).join('')}
      </div>
    `;
  }

  function renderRankingsTab() {
    const rk = state.data?.rankings || { men: [], women: [] };
    const hist = state.data?.history || [];
    const dbStats = state.data?.dbStats || {};

    return `
      <div class="summary-bar">
        <div class="summary-meta">
          <span>Ranking Oficial World Rugby</span>
          <span aria-hidden="true">·</span>
          <span>Historial DB: <strong>${dbStats.totalLoggedMatches || hist.length}</strong> partidos guardados</span>
        </div>
      </div>

      <div class="sub-seg" role="tablist">
        <button type="button" class="sub-btn ${state.rankMode === 'men' ? 'active' : ''}" data-rank="men">Masculino XV</button>
        <button type="button" class="sub-btn ${state.rankMode === 'women' ? 'active' : ''}" data-rank="women">Femenino XV</button>
        <button type="button" class="sub-btn ${state.rankMode === 'history' ? 'active' : ''}" data-rank="history">Historial (${hist.length})</button>
      </div>

      ${state.rankMode === 'history'
        ? `<div class="cards-grid">
            ${hist.map((h) => `
              <div class="match-card">
                <div class="card-kicker">
                  <span class="kicker-league">${esc(h.leagueName)}</span>
                  <span class="kicker-state state-finished">${esc(h.dateArg)}</span>
                </div>
                <div style="display:flex;justify-content:space-between;align-items:center;gap:12px;">
                  <strong>${esc(h.homeTeam)}</strong>
                  <span class="score-num" style="font-size:1.35rem;">${h.homeScore} - ${h.awayScore}</span>
                  <strong style="text-align:right;">${esc(h.awayTeam)}</strong>
                </div>
                <div class="card-foot"><span>${esc([h.venue, h.city].filter(Boolean).join(' · ') || 'Resultado registrado')}</span></div>
              </div>
            `).join('')}
          </div>`
        : `<table class="rank-table">
            <thead>
              <tr><th>Pos</th><th>Selección</th><th style="text-align:right;">Puntos</th></tr>
            </thead>
            <tbody>
              ${(rk[state.rankMode] || []).map((r) => `
                <tr ${r.team.toLowerCase().includes('argentina') ? 'style="background:rgba(56,189,248,0.09);"' : ''}>
                  <td class="mono-num" style="font-weight:700;color:var(--gold);">#${r.pos}</td>
                  <td>
                    <div style="display:flex;align-items:center;gap:10px;">
                      ${r.crestUrl ? `<img src="${esc(r.crestUrl)}" alt="${esc(r.team)}" style="width:24px;height:24px;object-fit:contain;" referrerpolicy="no-referrer" />` : ''}
                      <strong>${esc(r.team)}</strong>
                    </div>
                  </td>
                  <td class="mono-num" style="text-align:right;font-weight:700;">${Number(r.pts).toFixed(2)}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>`}
    `;
  }

  function renderTeamLineupBlock(team, sideLabel) {
    const list = team.lineup?.length ? team.lineup : (team.probableLineup || []);
    const isProbable = !team.lineup?.length && list.length > 0;
    const starters = list.filter((p) => p.starter);
    const subs = list.filter((p) => !p.starter);

    return `
      <div>
        <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px;">
          ${renderCrest(team, 30)}
          <div>
            <div style="font-weight:700;font-size:0.9rem;">${esc(team.name)}</div>
            <div style="font-size:0.74rem;color:var(--text-dim);">${team.coach ? `DT: ${esc(team.coach)}` : sideLabel}${isProbable ? ` · Último XV (${esc(team.probableFromDate)})` : ''}</div>
          </div>
        </div>
        ${list.length === 0
          ? `<div style="font-size:0.8rem;color:var(--text-dim);padding:8px 0;">Alineación oficial pendiente de confirmación previa al kick-off.</div>`
          : `
            <div style="font-size:0.72rem;font-weight:700;color:var(--emerald);margin:6px 0 4px;">TITULARES (${starters.length})</div>
            ${starters.map((p) => `
              <div class="player-row">
                <div><span class="jersey-num">${esc(p.jersey)}</span> ${esc(p.name)} ${p.captain ? '<strong style="color:var(--gold);">(C)</strong>' : ''}</div>
                <span style="color:var(--text-dim);font-size:0.75rem;">${esc(p.position)}</span>
              </div>
            `).join('')}
            ${subs.length ? `<div style="font-size:0.72rem;font-weight:700;color:var(--text-dim);margin:10px 0 4px;">SUPLENTES (${subs.length})</div>
              ${subs.map((p) => `
                <div class="player-row">
                  <div><span class="jersey-num">${esc(p.jersey)}</span> ${esc(p.name)}</div>
                  <span style="color:var(--text-dim);font-size:0.75rem;">${esc(p.position)}</span>
                </div>
              `).join('')}` : ''}
          `}
      </div>
    `;
  }

  function openMatchSheet(matchId) {
    const m = (state.data?.matches || []).find((x) => x.id === matchId);
    if (!m) return;
    const st = stateLabel(m);
    const venueStr = [m.venue?.name, m.venue?.city, m.venue?.country].filter(Boolean).join(' · ') || 'Estadio por confirmar';

    els.panel.innerHTML = `
      <div class="sheet-handle"></div>
      <div class="sheet-head">
        <div>
          <div style="font-size:0.75rem;font-weight:700;color:var(--gold);">${esc(m.leagueName)} ${m.phase ? `· ${esc(m.phase)}` : ''}</div>
          <div style="font-family:var(--font-display);font-size:1.5rem;font-weight:800;line-height:1.1;margin-top:2px;">
            ${esc(m.home.name)} vs ${esc(m.away.name)}
          </div>
          <div style="font-size:0.8rem;color:var(--text-dim);margin-top:4px;">${esc(st.txt)} · ${esc(venueStr)}</div>
        </div>
        <button id="closeSheetBtn" class="action-btn" type="button">Cerrar</button>
      </div>

      <div class="sheet-section">
        <div class="faceoff">
          <div class="team-col">
            ${renderCrest(m.home, 52)}
            <div class="team-info">
              <div class="team-name">${esc(m.home.name)}</div>
              <div class="team-sub">${m.home.coach ? `Head Coach: ${esc(m.home.coach)}` : 'Coach por confirmar'}</div>
            </div>
          </div>
          <div class="score-center">
            <div class="score-num">${m.status !== 'scheduled' && m.home.score !== null ? `${esc(m.home.score)} - ${esc(m.away.score)}` : 'VS'}</div>
            <div class="score-time">${esc(m.timeArg || '')}</div>
          </div>
          <div class="team-col away">
            ${renderCrest(m.away, 52)}
            <div class="team-info">
              <div class="team-name">${esc(m.away.name)}</div>
              <div class="team-sub">${m.away.coach ? `Head Coach: ${esc(m.away.coach)}` : 'Coach por confirmar'}</div>
            </div>
          </div>
        </div>
      </div>

      ${(m.scoring?.length || m.cards?.length || m.officials?.length) ? `
        <div class="sheet-section">
          <h3>Incidencias &amp; Autoridades</h3>
          ${m.scoring?.length ? `<div style="margin-bottom:10px;font-size:0.82rem;">
            ${m.scoring.map((s) => `<div style="padding:3px 0;"><strong style="color:var(--gold);">${esc(s.minute)}'</strong> ${esc(s.type)} — ${esc(s.player)} <span style="color:var(--text-dim);">(${esc(s.teamName)})</span></div>`).join('')}
          </div>` : ''}
          ${m.cards?.length ? `<div style="margin-bottom:10px;font-size:0.82rem;color:#fcd34d;">
            ${m.cards.map((c) => `<div>${esc(c.minute)}' Tarjeta ${esc(c.type)} — ${esc(c.player)}</div>`).join('')}
          </div>` : ''}
          ${m.officials?.length ? `<div style="font-size:0.8rem;color:var(--text-dim);">
            ${m.officials.map((o) => `<div><strong>${esc(o.role)}:</strong> ${esc(o.name)} ${o.country ? `(${esc(o.country)})` : ''}</div>`).join('')}
          </div>` : ''}
        </div>
      ` : ''}

      <div class="sheet-section">
        <h3>Alineaciones y Planteles</h3>
        <div class="lineups-split">
          ${renderTeamLineupBlock(m.home, 'Local')}
          ${renderTeamLineupBlock(m.away, 'Visitante')}
        </div>
      </div>
    `;
    els.sheet.classList.remove('hidden');
    document.getElementById('closeSheetBtn')?.addEventListener('click', closeSheet);
  }

  function openTeamSheet(teamKey) {
    const t = (state.data?.teamsDirectory || []).find((x) => x.key === teamKey);
    if (!t) return;
    els.panel.innerHTML = `
      <div class="sheet-handle"></div>
      <div class="sheet-head">
        <div style="display:flex;align-items:center;gap:12px;">
          ${renderCrest(t, 52)}
          <div>
            <div style="font-family:var(--font-display);font-size:1.5rem;font-weight:800;">${esc(t.name)}</div>
            <div style="font-size:0.8rem;color:var(--text-dim);">
              ${t.coach ? `Head Coach: ${esc(t.coach)}` : 'Head Coach oficial'} ${t.stadium ? `· ${esc(t.stadium)}` : ''}
            </div>
          </div>
        </div>
        <button id="closeSheetBtn" class="action-btn" type="button">Cerrar</button>
      </div>
      <div class="sheet-section">
        <h3>Último Plantel Verificado ${t.rosterUpdated ? `(${esc(t.rosterUpdated)})` : ''}</h3>
        ${(t.roster || []).length
          ? t.roster.map((p) => `
              <div class="player-row">
                <div><span class="jersey-num">${esc(p.jersey)}</span> ${esc(p.name)} ${p.captain ? '<strong style="color:var(--gold);">(C)</strong>' : ''}</div>
                <span style="color:var(--text-dim);font-size:0.78rem;">${esc(p.position)}${p.caps ? ` · ${p.caps} caps` : ''}</span>
              </div>`).join('')
          : '<div style="color:var(--text-dim);font-size:0.85rem;">Sin convocatoria reciente cargada todavía.</div>'}
      </div>
    `;
    els.sheet.classList.remove('hidden');
    document.getElementById('closeSheetBtn')?.addEventListener('click', closeSheet);
  }

  function closeSheet() {
    els.sheet.classList.add('hidden');
  }

  function render() {
    if (!state.data) return;
    els.navItems.forEach((b) => b.classList.toggle('active', b.dataset.tab === state.tab));

    if (state.tab === 'matches') els.main.innerHTML = renderMatchesTab();
    else if (state.tab === 'argentina') els.main.innerHTML = renderArgentinaTab();
    else if (state.tab === 'teams') els.main.innerHTML = renderTeamsTab();
    else if (state.tab === 'rankings') els.main.innerHTML = renderRankingsTab();

    bindDynamicEvents();
  }

  function bindDynamicEvents() {
    const searchEl = document.getElementById('searchInput');
    if (searchEl) {
      searchEl.addEventListener('input', (e) => {
        state.search = e.target.value;
        const pos = e.target.selectionStart;
        render();
        const nextEl = document.getElementById('searchInput');
        if (nextEl) {
          nextEl.focus();
          if (typeof pos === 'number') nextEl.setSelectionRange(pos, pos);
        }
      });
    }

    document.querySelectorAll('[data-group]').forEach((b) => {
      b.addEventListener('click', () => { state.group = b.dataset.group; render(); });
    });
    document.querySelectorAll('[data-status]').forEach((b) => {
      b.addEventListener('click', () => { state.statusFilter = b.dataset.status; render(); });
    });
    document.querySelectorAll('[data-rank]').forEach((b) => {
      b.addEventListener('click', () => { state.rankMode = b.dataset.rank; render(); });
    });
    document.querySelectorAll('[data-match-id]').forEach((b) => {
      b.addEventListener('click', () => openMatchSheet(b.dataset.matchId));
    });
    document.querySelectorAll('[data-team-key]').forEach((b) => {
      b.addEventListener('click', () => openTeamSheet(b.dataset.teamKey));
    });
  }

  async function loadData(force = false) {
    els.refreshBtn.textContent = 'Actualizando...';
    try {
      const url = `./data/rugby_engine_output.json${force ? `?t=${Date.now()}` : ''}`;
      const res = await fetch(url, { cache: force ? 'no-store' : 'default' });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      state.data = await res.json();
      render();
    } catch (err) {
      els.main.innerHTML = `<div class="empty-box">No se pudo cargar la base en vivo (${esc(err.message)}). Revisá tu conexión y tocá Actualizar.</div>`;
    } finally {
      els.refreshBtn.textContent = 'Actualizar';
    }
  }

  function setupPwa() {
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('./sw.js').catch(() => {});
    }

    const standalone = window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true;
    state.isInstalled = standalone;

    window.addEventListener('beforeinstallprompt', (e) => {
      e.preventDefault();
      state.deferredPrompt = e;
      if (!state.isInstalled) els.installBtn.classList.remove('hidden');
    });

    window.addEventListener('appinstalled', () => {
      state.isInstalled = true;
      state.deferredPrompt = null;
      els.installBtn.classList.add('hidden');
    });

    els.installBtn.addEventListener('click', async () => {
      if (!state.deferredPrompt) return;
      await state.deferredPrompt.prompt();
      const { outcome } = await state.deferredPrompt.userChoice;
      if (outcome === 'accepted') {
        state.deferredPrompt = null;
        els.installBtn.classList.add('hidden');
      }
    });

    const syncOnline = () => els.offlineBar.classList.toggle('hidden', navigator.onLine);
    window.addEventListener('online', syncOnline);
    window.addEventListener('offline', syncOnline);
    syncOnline();
  }

  els.navItems.forEach((btn) => {
    btn.addEventListener('click', () => {
      state.tab = btn.dataset.tab;
      window.scrollTo({ top: 0, behavior: 'instant' });
      render();
    });
  });

  els.refreshBtn.addEventListener('click', () => loadData(true));
  els.sheet.addEventListener('click', (e) => {
    if (e.target === els.sheet) closeSheet();
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closeSheet();
  });

  setupPwa();
  loadData(false);
})();
