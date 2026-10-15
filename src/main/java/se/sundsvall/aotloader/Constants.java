package se.sundsvall.aotloader;

/**
 * Names the migrated errands and permits share with the target systems and with pw-alkt.
 */
public final class Constants {

	/** The external tag that marks a migrated errand with the source item it came from; the key against duplicates. */
	public static final String TAG_MIGRATION_ID = "migreringsid";
	/** The external tag with the diary number of the source (decision 4). */
	public static final String TAG_DIARY_NUMBER = "diarienummer";
	/** The external tag of a notification, "{FSID}-{product}" (decision 22). */
	public static final String TAG_NOTIFICATION = "alkolAnmalan";

	/** The label that marks a migrated errand and keeps every process away from it (decision 17). */
	public static final String LABEL_MIGRATED = "TAGROOT/MIGRATED_ERRAND";
	/** The attribute of a label that keeps every process away from an errand that carries it. */
	public static final String LABEL_ATTRIBUTE_PROCESS_BLOCKED = "processBlocked";

	/** The parameter of the errand with the restaurant number of the serving place, as pw-alkt reads it (decision 18). */
	public static final String PARAMETER_RESTAURANT_NUMBER = "restaurantNumber";
	/** The parameters of the errand with the serving place or sales place, as pw-alkt reads them (section 7.2.1). */
	public static final String ERRAND_PARAMETER_PREMISES_NAME = "premisesName";
	public static final String ERRAND_PARAMETER_PREMISES_STREET_ADDRESS = "premisesStreetAddress";
	public static final String ERRAND_PARAMETER_PREMISES_POSTAL_CODE = "premisesPostalCode";
	public static final String ERRAND_PARAMETER_PREMISES_POSTAL_AREA = "premisesPostalArea";
	/**
	 * The parameters of the errand with the phone number and email address of the place, named like the keys pw-alkt reads.
	 */
	public static final String ERRAND_PARAMETER_PREMISES_PHONE = "premisesPhone";
	public static final String ERRAND_PARAMETER_PREMISES_EMAIL = "premisesEmail";
	/**
	 * The parameter of the errand and of the permit with the municipality code of the place, e.g. 2262 for Timrå, which
	 * picks e.g. the document templates (decision 19).
	 */
	public static final String PARAMETER_PREMISES_MUNICIPALITY_ID = "premisesMunicipalityId";
	/** The parameter that tells a person with significant influence apart from other stakeholders with the same role. */
	public static final String PARAMETER_PBI = "PBI";
	/** The display name of {@link #PARAMETER_PBI}. */
	public static final String PARAMETER_PBI_DISPLAY_NAME = "Person med betydande inflytande";
	/** The parameter of a person with significant influence with the person's positions, e.g. VD. */
	public static final String PARAMETER_POSITION = "befattning";
	/** The display name of {@link #PARAMETER_POSITION}. */
	public static final String PARAMETER_POSITION_DISPLAY_NAME = "Befattning";
	/** The parameter of the permit that links it to its errand, as pw-alkt writes it. */
	public static final String ASSET_PARAMETER_ERRAND_ID = "errandId";
	/** The parameter of the permit with its conditions, as pw-alkt writes it. */
	public static final String ASSET_PARAMETER_CONDITIONS = "conditions";
	public static final String ASSET_ORIGIN = "SUPPORTMANAGEMENT";
	/**
	 * The category of the permit certificate on a permit in PartyAssets, the one pw-alkt replaces when the permit changes.
	 */
	public static final String ASSET_CATEGORY_CERTIFICATE = "Tillståndsbevis";
	/** The category of the annex of the permit decision on a permit in PartyAssets. */
	public static final String ASSET_CATEGORY_ANNEX = "Beslutsbilaga";
	/** The category of a drawing of the premises on a permit in PartyAssets. */
	public static final String ASSET_CATEGORY_DRAWING = "Ritning";
	/** The category of the documents and attachments of the decisions of the issuing errand on a permit in PartyAssets. */
	public static final String ASSET_CATEGORY_DECISION = "Beslut";

	/** The longest value of an errand parameter in SupportManagement. */
	public static final int ERRAND_PARAMETER_MAX_LENGTH = 3000;
	/** The longest value of an additional parameter, the title or the description of a permit in PartyAssets. */
	public static final int ASSET_TEXT_MAX_LENGTH = 255;
	/** The longest reason, detail and message in the status table and the report, whose columns are varchar(4000). */
	public static final int REPORT_TEXT_MAX_LENGTH = 4000;

	public static final String RELATION_TYPE_LINK = "LINK";
	public static final String RESOURCE_TYPE_CASE = "case";
	/** The name pw-alkt and PartyAssets give SupportManagement in relations (section 7.7.1). */
	public static final String RELATION_SERVICE_SUPPORT_MANAGEMENT = "supportmanagement";

	private Constants() {}
}
