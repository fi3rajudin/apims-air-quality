import { findNearestStations } from './location-utils.js';

const readingsGrid = document.getElementById('readingsGrid');
const statusPanel = document.getElementById('statusPanel');
const stateFilter = document.getElementById('stateFilter');
const stationCount = document.getElementById('stationCount');
const refreshButton = document.getElementById('refreshButton');
const locationSearchForm = document.getElementById('locationSearchForm');
const locationSearchInput = document.getElementById('locationSearchInput');
const locationSearchButton = document.getElementById('locationSearchButton');
const locationSearchStatus = document.getElementById('locationSearchStatus');
const nearestSection = document.getElementById('nearestSection');
const searchedPlaceName = document.getElementById('searchedPlaceName');
const searchedCoordinates = document.getElementById('searchedCoordinates');
const nearestStationCard = document.getElementById('nearestStationCard');
const nearbyStations = document.getElementById('nearbyStations');

let allReadings = [];
const GEOCODE_CACHE_KEY = 'apims-geocode-cache-v2';

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}

function formatTime(value) {
    if (!value) return 'Not provided';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;

    return new Intl.DateTimeFormat('en-MY', {
        dateStyle: 'medium',
        timeStyle: 'short',
        timeZone: 'Asia/Kuala_Lumpur'
    }).format(date);
}

function getApiStatus(api, fallbackStatus = '') {
    const value = Number(api);

    if (!Number.isFinite(value)) {
        return fallbackStatus || 'N/A';
    }

    if (value <= 50) return 'Good';
    if (value <= 100) return 'Moderate';
    if (value <= 200) return 'Unhealthy';
    if (value <= 300) return 'Very Unhealthy';

    return 'Hazardous';
}

function apiStatusClass(status) {
    const normalized = String(status || '').trim().toLowerCase();

    // Check this BEFORE "unhealthy",
    // otherwise "very unhealthy" matches "unhealthy".
    if (normalized.includes('very unhealthy')) {
        return 'status-very-unhealthy';
    }

    if (normalized.includes('unhealthy')) {
        return 'status-unhealthy';
    }

    if (normalized.includes('hazard')) {
        return 'status-hazardous';
    }

    if (normalized.includes('moderate')) {
        return 'status-moderate';
    }

    if (normalized.includes('good')) {
        return 'status-good';
    }

    return 'status-na';
}

function stationTitle(item) {
    return item.stationLocation || item.place || item.stationId || 'Unknown station';
}

function populateStates(readings) {
    const selected = stateFilter.value;
    const states = [...new Set(readings.map(item => item.state).filter(Boolean))]
        .sort((a, b) => a.localeCompare(b));

    stateFilter.innerHTML = '<option value="">All states</option>' +
        states.map(state => `<option value="${escapeHtml(state)}">${escapeHtml(state)}</option>`).join('');

    if (states.includes(selected)) {
        stateFilter.value = selected;
    }
}

function renderReadingCard(item) {
    const status = getApiStatus(item.api, item.status);
    const statusClass = apiStatusClass(status);

    return `
        <article class="reading-card ${statusClass}-card">
            <div class="card-top">
                <div>
                    <h2 class="location">${escapeHtml(stationTitle(item))}</h2>
                    <p class="state">${escapeHtml(item.state || 'Unknown state')}</p>
                </div>

                <div class="api-display ${statusClass}-text">
                    <div class="api-value">${escapeHtml(item.api ?? '—')}</div>
                    <span class="api-label">API</span>
                </div>
            </div>

            <span class="badge ${statusClass}">
                ${escapeHtml(status)}
            </span>

            <div class="reading-meta">
                <div>
                    <span class="meta-label">Pollutant</span>
                    <span class="meta-value">
                        ${escapeHtml(item.pollutant || 'Not provided')}
                    </span>
                </div>

                <div>
                    <span class="meta-label">Updated</span>
                    <span class="meta-value">
                        ${escapeHtml(formatTime(item.readingTime))}
                    </span>
                </div>
            </div>
        </article>
    `;
}

function render() {
    const state = stateFilter.value;
    const readings = state
        ? allReadings.filter(item => item.state === state)
        : allReadings;

    stationCount.textContent = `${readings.length} station${readings.length === 1 ? '' : 's'}`;

    if (readings.length === 0) {
        readingsGrid.innerHTML = '';
        statusPanel.textContent = 'No readings found for this filter.';
        statusPanel.hidden = false;
        return;
    }

    statusPanel.hidden = true;
    readingsGrid.innerHTML = readings.map(renderReadingCard).join('');
}

function readGeocodeCache() {
    try {
        return JSON.parse(localStorage.getItem(GEOCODE_CACHE_KEY) || '{}');
    } catch {
        return {};
    }
}

function getCachedGeocode(query) {
    return readGeocodeCache()[query.trim().toLowerCase()] || null;
}

function cacheGeocode(query, result) {
    const cache = readGeocodeCache();
    cache[query.trim().toLowerCase()] = result;
    localStorage.setItem(GEOCODE_CACHE_KEY, JSON.stringify(cache));
}

async function geocodeMalaysiaLocation(query) {
    const cached = getCachedGeocode(query);
    if (cached) return cached;

    const response = await fetch(`/api/locations/search?q=${encodeURIComponent(query)}`, {
        cache: 'no-store'
    });

    let body = null;
    try {
        body = await response.json();
    } catch {
        body = null;
    }

    if (response.status === 404) {
        return null;
    }

    if (!response.ok) {
        throw new Error(body?.message || `Location search returned HTTP ${response.status}`);
    }

    const result = {
        name: body.name || query,
        latitude: Number(body.latitude),
        longitude: Number(body.longitude)
    };

    if (!Number.isFinite(result.latitude) || !Number.isFinite(result.longitude)) {
        throw new Error('Location search returned invalid coordinates.');
    }

    cacheGeocode(query, result);
    return result;
}

function renderNearestLocation(place) {
    const nearest = findNearestStations(place.latitude, place.longitude, allReadings, 4);
    if (nearest.length === 0) {
        throw new Error('No APIMS stations with usable coordinates are available.');
    }

    const primary = nearest[0];
    const primaryStatus = getApiStatus(primary.api, primary.status);
    searchedPlaceName.textContent = place.name;
    searchedCoordinates.textContent = `${place.latitude.toFixed(5)}, ${place.longitude.toFixed(5)}`;

    nearestStationCard.innerHTML = `
        <article class="nearest-card">
            <div class="nearest-main">
                <div>
                    <p class="nearest-distance">${primary.distanceKm.toFixed(1)} km from your searched location</p>
                    <h3>${escapeHtml(stationTitle(primary))}</h3>
                    <p class="nearest-place">${escapeHtml(primary.place || primary.state || '')}</p>
                    <span class="badge ${apiStatusClass(primaryStatus)}">${escapeHtml(primaryStatus)}</span>
                </div>
                <div class="nearest-api-block ${apiStatusClass(primaryStatus)}-text">
                    <span class="nearest-api">${escapeHtml(primary.api ?? '—')}</span>
                    <span>API</span>
                </div>
            </div>
            <div class="nearest-meta">
                <div><span>Pollutant</span><strong>${escapeHtml(primary.pollutant || 'Not provided')}</strong></div>
                <div><span>Station type</span><strong>${escapeHtml(primary.stationCategory || 'Not provided')}</strong></div>
                <div><span>Updated</span><strong>${escapeHtml(formatTime(primary.readingTime))}</strong></div>
            </div>
        </article>
    `;

    const secondary = nearest.slice(1);
    nearbyStations.innerHTML = secondary.length === 0 ? '' : `
        <h3 class="nearby-title">Other nearby stations</h3>
        <div class="nearby-list">
            ${secondary.map(item => `
                <div class="nearby-row">
                    <div>
                        <strong>${escapeHtml(stationTitle(item))}</strong>
                        <span>${escapeHtml(item.state || '')}</span>
                    </div>
                    <span>${item.distanceKm.toFixed(1)} km</span>
                    <span class="nearby-api ${apiStatusClass(getApiStatus(item.api, item.status))}">API ${escapeHtml(item.api ?? '—')}</span>
                </div>
            `).join('')}
        </div>
    `;

    nearestSection.hidden = false;
    nearestSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

async function searchLocation(event) {
    event.preventDefault();
    const query = locationSearchInput.value.trim();
    if (!query) return;

    if (allReadings.length === 0) {
        locationSearchStatus.textContent = 'Wait for the APIMS readings to finish loading first.';
        locationSearchStatus.classList.add('error-text');
        return;
    }

    locationSearchButton.disabled = true;
    locationSearchStatus.classList.remove('error-text');
    locationSearchStatus.textContent = `Searching Malaysia for “${query}”…`;

    try {
        const place = await geocodeMalaysiaLocation(query);
        if (!place) {
            throw new Error('No matching Malaysian location was found. Try a town, neighbourhood or landmark name.');
        }

        renderNearestLocation(place);
        locationSearchStatus.textContent = 'Nearest APIMS stations found.';
    } catch (error) {
        nearestSection.hidden = true;
        locationSearchStatus.classList.add('error-text');
        locationSearchStatus.textContent = error.message;
    } finally {
        locationSearchButton.disabled = false;
    }
}

async function loadReadings() {
    refreshButton.disabled = true;
    statusPanel.hidden = false;
    statusPanel.classList.remove('error');
    statusPanel.textContent = 'Loading current readings...';

    try {
        const response = await fetch('/api/readings', { cache: 'no-store' });
        const body = await response.json();

        if (!response.ok) {
            throw new Error(body.detail || body.message || `HTTP ${response.status}`);
        }

        allReadings = body;
        populateStates(allReadings);
        render();
    } catch (error) {
        readingsGrid.innerHTML = '';
        stationCount.textContent = '';
        statusPanel.hidden = false;
        statusPanel.classList.add('error');
        statusPanel.textContent = `Could not load APIMS readings: ${error.message}`;
    } finally {
        refreshButton.disabled = false;
    }
}

stateFilter.addEventListener('change', render);
refreshButton.addEventListener('click', loadReadings);
locationSearchForm.addEventListener('submit', searchLocation);
loadReadings();
