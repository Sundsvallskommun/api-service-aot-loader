package se.sundsvall.aotloader.service.model;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.unmodifiableMap;

/**
 * What the post in PartyAssets gets from the source. Its type and title come from the configuration of the errand type,
 * and its status from the assessment.
 *
 * @param titleSuffix         what follows the configured title, e.g. "detaljhandel", null for nothing
 * @param description         the description
 * @param issued              the date the permit was issued or the notification was made
 * @param validTo             the last day of the permit, null when it has no end
 * @param parameters          the additional parameters, without errandId, which is added when the errand exists
 * @param legalBasisDocuments the documents of the source that state the legal basis of the permit, in the order they
 *                            are
 *                            read: the first that states one gives it (decision 58); none for a notification
 */
public record AssetData(String titleSuffix, String description, LocalDate issued, LocalDate validTo, Map<String, String> parameters, List<FileReference> legalBasisDocuments) {

	public AssetData {
		parameters = parameters == null ? Map.of() : unmodifiableMap(new LinkedHashMap<>(parameters));
		legalBasisDocuments = legalBasisDocuments == null ? List.of() : legalBasisDocuments.stream().distinct().toList();
	}

	/**
	 * A post without documents that state its legal basis.
	 */
	public AssetData(final String titleSuffix, final String description, final LocalDate issued, final LocalDate validTo, final Map<String, String> parameters) {
		this(titleSuffix, description, issued, validTo, parameters, List.of());
	}

	/**
	 * @return the post with the parameter added, or replaced when it is there already
	 */
	public AssetData withParameter(final String key, final String value) {
		final var withValue = new LinkedHashMap<>(parameters);
		withValue.put(key, value);
		return new AssetData(titleSuffix, description, issued, validTo, withValue, legalBasisDocuments);
	}

	/**
	 * Names the parameters but shows none of their values, which hold the name and number of the holder (K6).
	 */
	@Override
	public String toString() {
		return "AssetData[issued=" + issued + ", validTo=" + validTo + ", parameters=" + parameters.keySet() + "]";
	}
}
