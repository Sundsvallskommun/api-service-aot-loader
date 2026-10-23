package se.sundsvall.aotloader.source;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.support.LegalIds;
import se.sundsvall.aotloader.support.Texts;

/**
 * Builds the additional parameters of a post in PartyAssets under the keys the AoT solution gives the permits it
 * creates, so that migrated and new permits read alike. A value the source does not have is left out, and so is a key
 * whose value only a handler gives in the AoT solution. A yes or no is "true" or "false", and the lines of a multiline
 * value are separated by line breaks.
 */
public final class AssetParameters {

	public static final String PERMIT_HOLDER_NAME = "permitHolderName";
	public static final String PERMIT_HOLDER_ORG_NUMBER = "permitHolderOrgNumber";
	public static final String PERMIT_HOLDER_STREET = "permitHolderStreet";
	public static final String PERMIT_HOLDER_POSTAL_ADDRESS = "permitHolderPostalAddress";
	public static final String PERMIT_HOLDER_PHONE = "permitHolderPhone";
	public static final String PREMISES_NAME = "premisesName";
	public static final String PREMISES_STREET = "premisesStreet";
	public static final String PREMISES_POSTAL_ADDRESS = "premisesPostalAddress";
	public static final String PREMISES_PHONE = "premisesPhone";
	public static final String PREMISES_RESTAURANT_NUMBER = "premisesRestaurantNumber";
	public static final String SERVING_TO_PUBLIC = "servingToPublic";
	public static final String SERVING_TO_CLOSED_COMPANY = "servingToClosedCompany";
	public static final String SCOPE_ALL_YEAR = "scopeAllYear";
	public static final String SCOPE_ANNUAL_PERIOD = "scopeAnnualPeriod";
	public static final String SCOPE_TASTING = "scopeTasting";
	public static final String SCOPE_PERIOD_FROM_TO = "scopePeriodFromTo";
	public static final String BEVERAGE_SPIRITS = "beverageSpirits";
	public static final String BEVERAGE_WINE = "beverageWine";
	public static final String BEVERAGE_BEER = "beverageBeer";
	public static final String BEVERAGE_OTHER_FERMENTED = "beverageOtherFermented";
	public static final String SERVING_AREA_DESCRIPTION = "servingAreaDescription";
	public static final String SERVING_AREA_SEATS = "servingAreaSeats";
	public static final String SERVING_AREA_MAX_PERSONS = "servingAreaMaxPersons";
	public static final String SERVING_HOURS = "servingHours";
	public static final String SALES_RETAIL = "salesRetail";
	public static final String SALES_WHOLESALE = "salesWholesale";
	public static final String SALES_ONLINE = "salesOnline";
	public static final String CASE_NUMBER = "caseNumber";
	public static final String DECISION_DATE_AND_CASE_NUMBER = "decisionDateAndCaseNumber";

	/** The diary number in the source (decision 4); the migration's own key, as the AoT solution has none for it. */
	public static final String DIARY_NUMBER = "diarienummer";
	/** The source the post was migrated from; the migration's own key. */
	public static final String MIGRATED_FROM = "migreradFran";
	/** The product of a notification; the migration's own key. */
	public static final String PRODUCT = "produkt";
	/** True when the date of a notification is the earliest date of its sales place; the migration's own key. */
	public static final String NOTIFIED_DATE_ESTIMATED = "anmaldUppskattat";

	private final Map<String, String> values = new LinkedHashMap<>();

	/**
	 * Adds the value, stripped, unless it is blank.
	 */
	public AssetParameters text(final String key, final String value) {
		final var stripped = Texts.trimToNull(value);
		if (stripped != null) {
			values.put(key, stripped);
		}
		return this;
	}

	public AssetParameters flag(final String key, final boolean value) {
		values.put(key, Boolean.toString(value));
		return this;
	}

	/**
	 * Adds the values that are not blank, one per line.
	 */
	public AssetParameters lines(final String key, final Collection<String> lines) {
		return text(key, Texts.join("\n", lines));
	}

	/**
	 * Adds the permit holder: its name, number, street, postal address and phone.
	 */
	public AssetParameters holder(final Holder holder) {
		if (holder == null) {
			return this;
		}
		return text(PERMIT_HOLDER_NAME, holder.name())
			.text(PERMIT_HOLDER_ORG_NUMBER, LegalIds.format(holder.legalId()))
			.text(PERMIT_HOLDER_STREET, holder.address())
			.text(PERMIT_HOLDER_POSTAL_ADDRESS, postalAddress(holder.zipCode(), holder.city()))
			.text(PERMIT_HOLDER_PHONE, holder.phone());
	}

	/**
	 * Adds the serving place or sales place: its name, street, postal address, phone and restaurant number. The postal code
	 * is without blanks, as on the errand.
	 */
	public AssetParameters premises(final Place place, final String restaurantNumber) {
		if (place == null) {
			return this;
		}
		return text(PREMISES_NAME, place.name())
			.text(PREMISES_STREET, place.address())
			.text(PREMISES_POSTAL_ADDRESS, postalAddress(Texts.withoutBlanks(place.zipCode()), place.city()))
			.text(PREMISES_PHONE, place.phone())
			.text(PREMISES_RESTAURANT_NUMBER, restaurantNumber);
	}

	public Map<String, String> build() {
		return new LinkedHashMap<>(values);
	}

	/**
	 * @return the postal code and the city, separated by a blank, e.g. "85230 Sundsvall"
	 */
	private static String postalAddress(final String zipCode, final String city) {
		return Texts.joinWords(zipCode, city);
	}
}
