package se.sundsvall.aotloader.service.model;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Collections.unmodifiableMap;

/**
 * What the post in PartyAssets gets from the source. Its type and title come from the configuration of the errand type,
 * and its status from the assessment.
 *
 * @param titleSuffix what follows the configured title, e.g. "detaljhandel", null for nothing
 * @param description the description
 * @param issued      the date the permit was issued or the notification was made
 * @param validTo     the last day of the permit, null when it has no end
 * @param parameters  the additional parameters, without errandId, which is added when the errand exists
 */
public record AssetData(String titleSuffix, String description, LocalDate issued, LocalDate validTo, Map<String, String> parameters) {

	public AssetData {
		parameters = parameters == null ? Map.of() : unmodifiableMap(new LinkedHashMap<>(parameters));
	}

	/**
	 * Names the parameters but shows none of their values, which hold the name and number of the holder (K6).
	 */
	@Override
	public String toString() {
		return "AssetData[issued=" + issued + ", validTo=" + validTo + ", parameters=" + parameters.keySet() + "]";
	}
}
