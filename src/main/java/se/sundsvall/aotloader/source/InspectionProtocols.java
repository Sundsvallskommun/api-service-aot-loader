package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.InvestigationTexts;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.support.LegalIds;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.Constants.ERRAND_PARAMETER_MAX_LENGTH;

/**
 * The inspection protocols Draken shows the investigation of an inspection errand as (decision 77): the template, by
 * the key Draken picks it by from the type of the investigation, and its fields as the parameters of the investigation,
 * by the keys and with the labels of the template. Only what the source tells is filled in; a field it has nothing for,
 * such as the summary or the number of guests, is left out, and Draken then shows it empty.
 */
public final class InspectionProtocols {

	/** The protocol of a visit to a place with a serving permit, "Serveringstillstånd – tillsyn". */
	public static final String SERVING_PERMIT = "serving_permit";
	/** The protocol of an inspection of the permit holder without a visit, "Inre tillsyn". */
	public static final String INTERNAL_INSPECTION = "internal_inspection";

	private InspectionProtocols() {}

	/**
	 * @param  place      the place visited, null when unknown
	 * @param  date       the day of the visit, null when unknown
	 * @param  start      the time the visit started, "hh:mm", null when unknown
	 * @param  end        the time it ended, "hh:mm", null when unknown
	 * @param  inspectors who made the visit, the first two of whom fill the protocol's two fields
	 * @param  texts      the texts of the visit, written as its description
	 * @return            the fields of the protocol {@link #SERVING_PERMIT}
	 */
	public static List<Parameter> visit(final Place place, final LocalDate date, final String start, final String end, final List<String> inspectors, final InvestigationTexts texts) {
		final var fields = new ArrayList<Parameter>();
		if (place != null) {
			add(fields, "premisesName", "Restaurang", place.name());
			add(fields, "premisesAddress", "Adress", address(place));
		}
		add(fields, "inspectionDate", "Tillsynsdatum", SourceValues.format(date));
		add(fields, "visitStartTime", "Inleds kl.", clock(start));
		add(fields, "visitEndTime", "Avslutades kl.", clock(end));
		add(fields, "additionalRemarks", "Beskrivning", plain(texts));
		add(fields, "inspector1NameAndTitle", "Namn, befattning", inspectors.isEmpty() ? null : inspectors.getFirst());
		add(fields, "inspector2NameAndTitle", "Namn, befattning", inspectors.size() < 2 ? null : inspectors.get(1));
		return fields;
	}

	/**
	 * @param  holder   the holder the inspection concerned, null when unknown
	 * @param  place    the place of the holder, null when unknown
	 * @param  type     the type of the inspection errand in the source
	 * @param  date     the day the inspection started, null when unknown
	 * @param  handlers who made the inspection
	 * @return          the fields of the protocol {@link #INTERNAL_INSPECTION}
	 */
	public static List<Parameter> internal(final Holder holder, final Place place, final String type, final LocalDate date, final List<String> handlers) {
		final var fields = new ArrayList<Parameter>();
		if (holder != null) {
			add(fields, "businessName", "Verksamhetens namn", holder.name());
			add(fields, "businessOrgNumber", "Organisationsnummer", LegalIds.format(holder.legalId()));
		}
		if (place != null) {
			add(fields, "businessAddress", "Adress", address(place));
		}
		add(fields, "inspectionType", "Typ av tillsyn", type);
		add(fields, "inspectionDate", "Datum", SourceValues.format(date));
		add(fields, "inspectorName", "Utförd av", Texts.join(", ", handlers));
		return fields;
	}

	private static void add(final List<Parameter> fields, final String key, final String label, final String value) {
		final var trimmed = Texts.trimToNull(value);
		if (trimmed != null) {
			fields.add(new Parameter(key, label, List.of(Texts.truncate(trimmed, ERRAND_PARAMETER_MAX_LENGTH, null))));
		}
	}

	private static String address(final Place place) {
		return Texts.join(", ", Arrays.asList(place.address(), Texts.joinWords(place.zipCode(), place.city())));
	}

	/**
	 * @return the time as Draken takes it, "hh:mm" with 24:00 as 00:00; null for one that cannot be read
	 */
	private static String clock(final String time) {
		return SourceValues.parseTime(time).map(value -> "24:00".equals(value) ? "00:00" : value).orElse(null);
	}

	/**
	 * @return the texts as plain text, each part under its heading; null without texts
	 */
	private static String plain(final InvestigationTexts texts) {
		return Texts.trimToNull(String.join("\n\n", texts.parts().stream().map(part -> part.heading() + ":\n" + part.text()).toList()));
	}
}
