package dataRepo.userDetails;

import dataRepo.appConfigs.AppConfigs;

/**
 * Geolocation model. Plaintext HTTP lookup to ip-api.com is disabled to protect
 * user IP privacy and comply with strict HTTPS cleartext restrictions.
 */
public final class GeoDetails {

    public String countryCode = "";
    public String languageCode = "";
    public String locationCity = "";
    public String locationRegion = "";
    public String networkIsp = "";
    public String userIpAddress = "";
    public String continent = "";
    public String continentCode = "";
    public String zipCode = "";

    public static void fetch(OnLocationDataListener listener, AppConfigs appConfig) {
        // Disabled: no plaintext HTTP IP geolocation requests are sent.
    }

    public interface OnLocationDataListener {
        void onComplete(GeoDetails geoDetails);
    }
}
