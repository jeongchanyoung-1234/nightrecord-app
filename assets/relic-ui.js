import { JOB_NAMES, jobLabel } from './parser.js';
import {
  enrichRelics,
  mergeShopRelics,
  ensureRequiredItemPlaceholders,
  recommendLoadouts,
  loadRelicCatalog,
  loadRelicEffects,
  loadRelicKo,
  loadRelicBuilds,
  loadRelicEffectMeta,
  loadRelicScoreRules,
  loadRelicEffectNames,
  buildsForJob,
  colorLabel,
  resolveBuild,
  buildDisplayName,
  buildDisplaySummary,
  vesselDisplayName,
} from './relics.js?v=53';
import { t, getLocale } from './i18n.js?v=12';

const $ = (selector, root = document) => root.querySelector(selector);

let relicCatalog = null;
let relicEffects = null;
let relicKo = null;
let relicBuilds = null;
let relicEffectMeta = null;

export const relicState = {
  slotIndex: 0,
  jobId: 1,
  buildId: '',
  vesselName: '',
  includeDeep: true,
  includeShop: true,
};

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

async function ensureRelicData() {
  if (!relicCatalog) relicCatalog = await loadRelicCatalog();
  if (!relicEffects) relicEffects = await loadRelicEffects();
  if (!relicKo) relicKo = await loadRelicKo();
  if (!relicBuilds) relicBuilds = await loadRelicBuilds();
  if (!relicEffectMeta) relicEffectMeta = await loadRelicEffectMeta();
  await loadRelicScoreRules();
  await loadRelicEffectNames();
  return {
    catalog: relicCatalog,
    effectById: relicEffects,
    ko: relicKo,
    builds: relicBuilds,
    effectMeta: relicEffectMeta,
  };
}

function colorChipHtml(catalog, color, deep = false) {
  const label = colorLabel(catalog, color, false);
  return `<span class="relic-color c${color}${deep ? ' deep' : ''}" title="${escapeHtml(label)}">${escapeHtml(label)}</span>`;
}

function relicTagHtml(relic) {
  const tags = [];
  if (relic?.isJournal || relic?.shopSource === 'quest') {
    tags.push(`<span class="relic-tag shop">${escapeHtml(t('relics.journal'))}</span>`);
  } else if (relic?.isShop) {
    tags.push(`<span class="relic-tag shop">${escapeHtml(relic.shopSource === 'signboard' ? t('relics.collector') : t('relics.shop'))}</span>`);
  }
  if (relic?.owned === false) tags.push(`<span class="relic-tag missing">${escapeHtml(t('relics.missing'))}</span>`);
  return tags.length ? `<span class="relic-tags">${tags.join('')}</span>` : '';
}

function relicEffectLineHtml(effect, highlight = null) {
  const id = Number(effect?.id);
  const name = escapeHtml(effect?.name || t('relics.effect', { id }));
  const required = highlight?.required?.has(id);
  const semi = highlight?.semiRequired?.has(id);
  const cls = required ? 'fx-main fx-required' : semi ? 'fx-main fx-semi' : 'fx-main';
  const main = `<span class="${cls}">${name}</span>`;
  if (!effect.curse?.name) return `<li>${main}</li>`;
  return `<li class="fx-pair">${main}<span class="fx-sep"> | </span><span class="fx-curse">${escapeHtml(effect.curse.name)}</span></li>`;
}

function relicCardHtml(catalog, relic, highlight = null) {
  if (!relic) {
    return `<div class="relic-mini empty"><span class="muted">${escapeHtml(t('relics.emptySlot'))}</span></div>`;
  }
  const effects = relic.effects.map((effect) => relicEffectLineHtml(effect, highlight)).join('');
  const itemMark = highlight?.requiredItems?.has(Number(relic.itemId)) ? ' required-item' : '';
  return `
    <div class="relic-mini c${relic.color}${relic.isDeep ? ' deep' : ''}${relic.owned === false ? ' missing' : ''}${itemMark}">
      <div class="relic-mini-head">
        ${colorChipHtml(catalog, relic.color, relic.isDeep)}
        <strong>${escapeHtml(relic.label)}</strong>
        ${relicTagHtml(relic)}
      </div>
      <ul>${effects || `<li class="muted">${escapeHtml(t('relics.noEffects'))}</li>`}</ul>
    </div>
  `;
}

function effectNameById(id, effectById, ko) {
  const key = String(id);
  return ko?.effects?.[key] || effectById?.[key]?.name || effectById?.[id]?.name || t('relics.effect', { id });
}

function missingRequirementsHtml(missing, effectById, ko, catalog) {
  const parts = [];
  for (const id of missing.effects || []) {
    parts.push(`<li><span class="fx-main fx-required">${escapeHtml(effectNameById(id, effectById, ko))}</span></li>`);
  }
  for (const id of missing.items || []) {
    const item = catalog?.items?.[String(id)] || catalog?.items?.[id];
    const name = ko?.items?.[String(id)] || item?.name || t('relics.item', { id });
    parts.push(`<li><span class="fx-main fx-required">${escapeHtml(name)}</span> <span class="muted">${escapeHtml(t('relics.fixedRelic'))}</span></li>`);
  }
  return `
    <div class="empty-card">
      <p>${escapeHtml(t('relics.noReq'))}</p>
      <ul class="missing-req">${parts.join('')}</ul>
    </div>
  `;
}

export function bindRelicControls(onChange) {
  document.addEventListener('change', (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    if (target.id === 'relicSlotSelect') {
      relicState.slotIndex = Number(target.value) || 0;
      onChange();
    } else if (target.id === 'relicJobSelect') {
      relicState.jobId = Number(target.value) || 1;
      relicState.buildId = '';
      onChange();
    } else if (target.id === 'relicBuildSelect') {
      relicState.buildId = target.value || '';
      onChange();
    } else if (target.id === 'relicVesselSelect') {
      relicState.vesselName = target.value || '';
      onChange();
    } else if (target.id === 'relicIncludeDeep') {
      relicState.includeDeep = target.checked;
      onChange();
    } else if (target.id === 'relicIncludeShop') {
      relicState.includeShop = target.checked;
      onChange();
    }
  });
}

export async function renderRelicRecommender({ relicSlots, allowed = true, deniedMessage = '' } = {}) {
  const controls = $('#relicsControls');
  const results = $('#relicsResults');
  if (!controls || !results) return;

  if (!allowed) {
    controls.innerHTML = '';
    results.innerHTML = `<div class="empty-card">${escapeHtml(deniedMessage || t('relics.deniedShort'))}</div>`;
    return;
  }

  if (!relicSlots?.length) {
    controls.innerHTML = '';
    results.innerHTML = `<div class="empty-card">${escapeHtml(t('relics.emptyInv'))}</div>`;
    return;
  }

  try {
    const { catalog, effectById, ko, builds, effectMeta } = await ensureRelicData();
    const slot = relicSlots.find((item) => item.index === relicState.slotIndex) || relicSlots[0];
    relicState.slotIndex = slot.index;
    const ownedRelics = enrichRelics(slot.relics, catalog, effectById, ko, effectMeta);
    const shopMerged = relicState.includeShop
      ? mergeShopRelics(ownedRelics, catalog, effectById, ko, effectMeta)
      : ownedRelics;
    const nightfarer = catalog.nightfarers.find((entry) => entry.jobId === relicState.jobId) || catalog.nightfarers[0];
    relicState.jobId = nightfarer.jobId;
    const jobBuilds = buildsForJob(builds, relicState.jobId);
    if (!relicState.buildId || !jobBuilds.some((build) => build.id === relicState.buildId)) {
      relicState.buildId = jobBuilds[0]?.id || '';
    }
    const activeBuild = jobBuilds.find((build) => build.id === relicState.buildId) || jobBuilds[0];
    const relics = ensureRequiredItemPlaceholders(
      shopMerged,
      activeBuild,
      catalog,
      effectById,
      ko,
      effectMeta,
    );
    const vessels = (nightfarer.vessels || []).map((vessel) => ({
      ...vessel,
      displayName: vesselDisplayName(vessel.name, relicKo),
    }));
    if (!relicState.vesselName || !vessels.some((vessel) => vessel.name === relicState.vesselName)) {
      relicState.vesselName = '';
    }

    const slotOptions = relicSlots.map((item) => (
      `<option value="${item.index}" ${item.index === relicState.slotIndex ? 'selected' : ''}>${escapeHtml(item.name)}</option>`
    )).join('');
    const jobOptions = Object.keys(JOB_NAMES).map((id) => (
      `<option value="${id}" ${Number(id) === relicState.jobId ? 'selected' : ''}>${escapeHtml(jobLabel(Number(id), getLocale()))}</option>`
    )).join('');
    const buildOptions = jobBuilds.map((build) => (
      `<option value="${escapeHtml(build.id)}" ${build.id === relicState.buildId ? 'selected' : ''}>${escapeHtml(buildDisplayName(build))}</option>`
    )).join('');
    const vesselOptions = [
      `<option value="" ${!relicState.vesselName ? 'selected' : ''}>${escapeHtml(t('relics.vesselAuto'))}</option>`,
      ...vessels.map((vessel) => {
        const chips = vessel.slots.map((color, index) => colorLabel(catalog, color, index >= 3)).join(' / ');
        return `<option value="${escapeHtml(vessel.name)}" ${relicState.vesselName === vessel.name ? 'selected' : ''}>${escapeHtml(vessel.displayName)} · ${escapeHtml(chips)}</option>`;
      }),
    ].join('');

    const buildSummary = buildDisplaySummary(activeBuild);
    const buildSummaryHtml = buildSummary
      ? `<div class="relic-build-summary" role="note">${escapeHtml(buildSummary)}</div>`
      : '';

    controls.innerHTML = `
      <label class="relic-field">
        <span>${escapeHtml(t('relics.slot'))}</span>
        <select id="relicSlotSelect">${slotOptions}</select>
      </label>
      <label class="relic-field">
        <span>${escapeHtml(t('relics.job'))}</span>
        <select id="relicJobSelect">${jobOptions}</select>
      </label>
      <label class="relic-field">
        <span>${escapeHtml(t('relics.build'))}</span>
        <select id="relicBuildSelect">${buildOptions}</select>
      </label>
      <label class="relic-field">
        <span>${escapeHtml(t('relics.vessel'))}</span>
        <select id="relicVesselSelect">${vesselOptions}</select>
      </label>
      <label class="relic-toggle">
        <input id="relicIncludeDeep" type="checkbox" ${relicState.includeDeep ? 'checked' : ''} />
        <span>${escapeHtml(t('relics.includeDeep'))}</span>
      </label>
      <label class="relic-toggle">
        <input id="relicIncludeShop" type="checkbox" ${relicState.includeShop ? 'checked' : ''} />
        <span>${escapeHtml(t('relics.includeShop'))}</span>
      </label>
      ${buildSummaryHtml}
    `;

    const resolvedBuild = resolveBuild(activeBuild, builds, effectMeta, { jobId: relicState.jobId });
    const highlight = {
      required: new Set((resolvedBuild?.required || []).map(Number)),
      semiRequired: new Set((resolvedBuild?.semiRequired || []).map(Number)),
      requiredItems: new Set((resolvedBuild?.requiredItems || []).map(Number)),
    };

    const rec = recommendLoadouts({
      relics,
      catalog,
      jobId: relicState.jobId,
      build: activeBuild,
      builds,
      vesselName: relicState.vesselName || null,
      includeDeep: relicState.includeDeep,
      limit: 8,
      ko,
      effectMeta,
      effectById,
    });

    if (rec.missingRequirements) {
      results.innerHTML = missingRequirementsHtml(rec.missingRequirements, effectById, ko, catalog);
      return;
    }

    if (!rec.loadouts.length) {
      results.innerHTML = `<div class="empty-card">${escapeHtml(t('relics.notEnough'))}</div>`;
      return;
    }

    results.innerHTML = rec.loadouts.map((loadout, rank) => {
      const slotChips = loadout.slots.map((color, index) => colorChipHtml(catalog, color, index >= 3)).join('');
      const cards = loadout.relics.map((relic) => relicCardHtml(catalog, relic, highlight)).join('');
      return `
        <article class="relic-loadout">
          <header>
            <span class="rank">#${rank + 1}</span>
            <strong>${escapeHtml(loadout.vesselKo || loadout.vessel)}</strong>
            <span class="slot-chips">${slotChips}</span>
            <span class="relic-score total">${escapeHtml(t('relics.score', { n: loadout.displayScore ?? loadout.score }))}</span>
          </header>
          <div class="relic-slot-grid">${cards}</div>
        </article>
      `;
    }).join('');
  } catch (error) {
    controls.innerHTML = '';
    results.innerHTML = `<div class="empty-card">${escapeHtml(error?.message || t('relics.fail'))}</div>`;
  }
}
