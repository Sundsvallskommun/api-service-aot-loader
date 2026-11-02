package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import se.sundsvall.aotloader.service.model.AssetData;
import se.sundsvall.aotloader.service.model.AssetSchema;
import se.sundsvall.aotloader.service.model.DecisionData;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.support.DiaryNumbers;
import se.sundsvall.aotloader.support.JsonFields;
import se.sundsvall.aotloader.support.LegalIds;
import se.sundsvall.aotloader.support.Texts;

/**
 * Builds the JSON parameter of a post in PartyAssets by the AoT schemas (decision 87), the same way for both sources:
 * the fields both schemas have here, those of one schema through {@link #fields()}. A value the source does not have is
 * left out, and so is one that does not follow the schema, which {@link #omitted()} then names: a postal code, an email
 * address, a restaurant number or a number that does not have the form the schema wants. The fields are added in the
 * order of the schema.
 */
public final class AssetContents {

	public static final String FIELD_ERRAND_ID = "errandId";
	public static final String FIELD_CASE_REGISTRATION_NUMBER = "caseRegistrationNumber";
	public static final String FIELD_EXTERNAL_CASE_REGISTRATION_NUMBER = "externalCaseRegistrationNumber";
	public static final String FIELD_PERMIT_HOLDER = "permitHolder";
	public static final String FIELD_MUNICIPALITY_ID = "municipalityId";
	public static final String FIELD_CONDITIONS = "conditions";
	public static final String FIELD_DECISION = "decision";
	public static final String FIELD_DECISION_TEXT = "decisionText";
	public static final String FIELD_LEGAL_BASIS = "legalBasis";
	public static final String FIELD_MIGRATION = "migration";

	private static final Pattern POSTAL_CODE = Pattern.compile("\\d{5}");
	private static final Pattern RESTAURANT_NUMBER = Pattern.compile("\\d{8}");
	// Only addresses that the format email of the json-schema service also takes, so that an address it would refuse is
	// left out rather than refused with the whole post; one with å, ä or ö is left out too
	private static final Pattern EMAIL_ADDRESS = Pattern.compile("[\\w%+-]++(?:\\.[\\w%+-]++)*+@(?:\\p{Alnum}++(?:-++\\p{Alnum}++)*+\\.)++\\p{Alpha}{2,63}+");
	private static final Pattern COUNT = Pattern.compile("\\d{1,9}");

	private final AssetSchema schema;
	private final JsonFields root = JsonFields.object();
	private final List<String> omitted = new ArrayList<>();
	private String diaryNumber;

	public AssetContents(final AssetSchema schema) {
		this.schema = schema;
	}

	/**
	 * @return the fields of the JSON parameter, to which the fields of one schema are added
	 */
	public JsonFields fields() {
		return root;
	}

	/**
	 * Adds the diary number of the errand that granted the permit or took the notification in, both as its own and as its
	 * number in the other register: AlkT and AlkOL keep no register of their own, so every diary number in them is the
	 * board's, or Timrå's for a place in Timrå. A reference to a delegation decision written after the number is parted
	 * from it when a decision is added that takes the reference, see {@link #decision}.
	 */
	public AssetContents caseRegistrationNumber(final String diaryNumber) {
		this.diaryNumber = diaryNumber;
		root.text(FIELD_CASE_REGISTRATION_NUMBER, diaryNumber)
			.text(FIELD_EXTERNAL_CASE_REGISTRATION_NUMBER, diaryNumber);
		return this;
	}

	/**
	 * Adds the permit holder: its name, organization number, address and phone. A sole trader has no organization number,
	 * as the personal identity number is not written out (decision 81).
	 */
	public AssetContents permitHolder(final Holder holder) {
		if (holder != null) {
			root.object(FIELD_PERMIT_HOLDER, JsonFields.object()
				.text("name", holder.name())
				.text("organizationNumber", LegalIds.formatOrganizationNumber(holder.legalId()))
				.object("address", address(FIELD_PERMIT_HOLDER, holder.address(), holder.zipCode(), holder.city()))
				.text("phoneNumber", holder.phone()));
		}
		return this;
	}

	/**
	 * Adds the serving place or sales place, the field of the schema: its name, address, phone, email address and, for a
	 * serving place, its restaurant number. Its municipality is added when the post is written.
	 *
	 * @param restaurantNumber the restaurant number, null for none, and for a sales place of the tobacco schema, which has
	 *                         no such field
	 */
	public AssetContents place(final Place place, final String restaurantNumber) {
		if (place == null) {
			return this;
		}
		final var field = schema.placeField();
		final var fields = JsonFields.object()
			.text("name", place.name())
			.object("address", address(field, place.address(), place.zipCode(), place.city()))
			.text("phoneNumber", place.phone())
			.text("emailAddress", checked(field + ".emailAddress", place.email(), EMAIL_ADDRESS, "har inte formen av en e-postadress"));
		if (schema == AssetSchema.ALCOHOL) {
			fields.text("restaurantNumber", checked(field + ".restaurantNumber", restaurantNumber, RESTAURANT_NUMBER, "har inte åtta siffror"));
		}
		root.object(field, fields);
		return this;
	}

	/**
	 * Adds the conditions of the decision, one per entry.
	 */
	public AssetContents conditions(final List<String> conditions) {
		root.texts(FIELD_CONDITIONS, conditions);
		return this;
	}

	/**
	 * Adds the decision the permit was granted by. The text of its outcome and its legal basis are added when the post is
	 * written. The decision maker is left out when the source names nobody.
	 * <p>
	 * A reference to a delegation decision written after the diary number of the decision, or else after the diary number
	 * of the permit, as in "MBN/2019:123 MBN DEL/2019 §45", is the decision's delegation reference, and the diary numbers
	 * are written without it (decision 89).
	 *
	 * @param decision         the decision, null when the permit has none
	 * @param issued           the day the permit was issued, which is the day the decision was sent out
	 * @param replacesDecision the earlier decision it replaces, as the source writes it, null when it replaces none
	 */
	public AssetContents decision(final DecisionData decision, final LocalDate issued, final String replacesDecision) {
		if (decision == null) {
			return this;
		}
		final var own = DiaryNumbers.split(decision.caseNumber());
		final var permits = DiaryNumbers.split(diaryNumber);
		final var delegationReference = own.delegationReference() != null ? own.delegationReference() : permits.delegationReference();
		if (permits.delegationReference() != null && permits.delegationReference().equals(delegationReference)) {
			root.text(FIELD_CASE_REGISTRATION_NUMBER, permits.number())
				.text(FIELD_EXTERNAL_CASE_REGISTRATION_NUMBER, permits.number());
		}
		root.object(FIELD_DECISION, JsonFields.object()
			.text(FIELD_CASE_REGISTRATION_NUMBER, own.number())
			.text("outcome", decision.outcome())
			.text("justification", decision.justification())
			.text("decisionMaker", decision.maker())
			.text("caseOfficer", decision.caseOfficer())
			.date("decisionDate", decision.decidedAt().toLocalDate())
			.date("executionDate", issued)
			.date("validFrom", decision.validFrom())
			.text("delegationReference", delegationReference)
			.text("replacesDecision", replacesDecision));
		return this;
	}

	/**
	 * Adds where the post was migrated from.
	 *
	 * @param issuedDateEstimated true when the day the post was issued is the earliest day of its place, not its own
	 */
	public AssetContents migration(final Source source, final boolean issuedDateEstimated) {
		root.object(FIELD_MIGRATION, JsonFields.object()
			.text("sourceSystem", source.name())
			.flag("issuedDateEstimated", issuedDateEstimated));
		return this;
	}

	/**
	 * @param  path  the path of the field, e.g. "servingArea.seatsIndoor"
	 * @param  value the number as the source writes it
	 * @return       the number, null when the source has none or it is not a whole number, which is then named among
	 *               what is left out
	 */
	public Integer count(final String path, final String value) {
		final var stripped = Texts.trimToNull(value);
		if (stripped == null) {
			return null;
		}
		if (!COUNT.matcher(stripped).matches()) {
			omitted.add(path + ": inte ett heltal");
			return null;
		}
		return Integer.valueOf(stripped);
	}

	/**
	 * Names a field that is left out, and why.
	 */
	public AssetContents omit(final String path, final String why) {
		omitted.add(path + ": " + why);
		return this;
	}

	/**
	 * @return the post with the JSON parameter as built and what it leaves out
	 */
	public AssetData asset(final String titleSuffix, final String description, final LocalDate issued, final LocalDate validTo,
		final List<FileReference> legalBasisDocuments) {
		return new AssetData(titleSuffix, description, issued, validTo, schema, root.toNode(), omitted, null, legalBasisDocuments);
	}

	/**
	 * @return the address, the postal code without blanks, as on the errand; a postal code that is not five digits is left
	 *         out
	 */
	private JsonFields address(final String field, final String street, final String zipCode, final String city) {
		return JsonFields.object()
			.text("street", street)
			.text("postalCode", checked(field + ".address.postalCode", Texts.withoutBlanks(zipCode), POSTAL_CODE, "har inte fem siffror"))
			.text("city", city);
	}

	/**
	 * @return the value when it has the form, null when it is blank or does not, which is then named among what is left out
	 */
	private String checked(final String path, final String value, final Pattern form, final String why) {
		final var stripped = Texts.trimToNull(value);
		if (stripped == null || form.matcher(stripped).matches()) {
			return stripped;
		}
		omitted.add(path + ": " + why);
		return null;
	}
}
