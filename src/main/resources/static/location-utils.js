export function haversineDistanceKm(lat1, lon1, lat2, lon2) {
    const toRad = degrees => degrees * Math.PI / 180;
    const earthRadiusKm = 6371.0088;

    const dLat = toRad(lat2 - lat1);
    const dLon = toRad(lon2 - lon1);
    const a = Math.sin(dLat / 2) ** 2
        + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2))
        * Math.sin(dLon / 2) ** 2;

    return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export function findNearestStations(latitude, longitude, readings, limit = 4) {
    return readings
        .filter(item => item.latitude !== null && item.latitude !== '' && item.longitude !== null && item.longitude !== ''
            && Number.isFinite(Number(item.latitude)) && Number.isFinite(Number(item.longitude)))
        .map(item => ({
            ...item,
            distanceKm: haversineDistanceKm(
                latitude,
                longitude,
                Number(item.latitude),
                Number(item.longitude)
            )
        }))
        .sort((a, b) => a.distanceKm - b.distanceKm)
        .slice(0, limit);
}
