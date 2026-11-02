package se.sundsvall.aotloader.service.model;

import java.time.LocalDate;
import java.util.List;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import static java.util.Objects.requireNonNull;

/**
 * What the post in PartyAssets gets from the source. Its type and title come from the configuration of the errand type,
 * and its status from the assessment.
 *
 * @param titleSuffix         what follows the configured title, e.g. "detaljhandel", null for nothing
 * @param description         the description
 * @param issued              the date the permit was issued or the notification was made
 * @param validTo             the last day of the permit, null when it has no end
 * @param schema              the AoT schema of its JSON parameter (decision 87)
 * @param content             the JSON parameter as the source gives it, without the id of the errand, the
 *                            municipality of the place and, of the decision, the legal basis and the text of the
 *                            outcome, which come when the post is written
 * @param omitted             what the source has but the JSON parameter leaves out, since it does not follow the
 *                            schema, as the path of the field and why, without the value
 * @param legalBasis          the legal basis read from the documents of the permit, null when none is read
 * @param legalBasisDocuments the documents of the source that state the legal basis of the permit, in the order they
 *                            are read: the first that states one gives it (decision 58); none for a notification
 */
public record AssetData(String titleSuffix, String description, LocalDate issued, LocalDate validTo, AssetSchema schema, ObjectNode content, List<String> omitted,
	String legalBasis, List<FileReference> legalBasisDocuments) {

	public AssetData {
		requireNonNull(schema, "schema");
		content = content == null ? JsonNodeFactory.instance.objectNode() : content.deepCopy();
		omitted = omitted == null ? List.of() : List.copyOf(omitted);
		legalBasisDocuments = legalBasisDocuments == null ? List.of() : legalBasisDocuments.stream().distinct().toList();
	}

	/**
	 * A post without documents that state its legal basis.
	 */
	public AssetData(final String titleSuffix, final String description, final LocalDate issued, final LocalDate validTo, final AssetSchema schema, final ObjectNode content,
		final List<String> omitted) {
		this(titleSuffix, description, issued, validTo, schema, content, omitted, null, List.of());
	}

	/**
	 * @return a copy of the JSON parameter, which the caller may change
	 */
	@Override
	public ObjectNode content() {
		return content.deepCopy();
	}

	/**
	 * @return the post with the legal basis read from its documents
	 */
	public AssetData withLegalBasis(final String value) {
		return new AssetData(titleSuffix, description, issued, validTo, schema, content, omitted, value, legalBasisDocuments);
	}

	/**
	 * Names the fields of the JSON parameter but shows none of their values, which hold the name and number of the holder
	 * and the serving staff (K6).
	 */
	@Override
	public String toString() {
		return "AssetData[issued=" + issued + ", validTo=" + validTo + ", schema=" + schema + ", content=" + content.propertyNames() + ", omitted=" + omitted.size() + "]";
	}
}
