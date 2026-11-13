package se.sundsvall.aotloader.service.mapper;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.JsonContent;
import se.sundsvall.aotloader.service.model.JsonParameterType;
import se.sundsvall.aotloader.source.AssetContents;
import se.sundsvall.aotloader.support.FieldForms;
import se.sundsvall.aotloader.support.JsonFields;
import se.sundsvall.aotloader.support.Omissions;
import se.sundsvall.aotloader.support.Texts;
import tools.jackson.databind.node.ObjectNode;

import static se.sundsvall.aotloader.service.model.JsonParameterType.ECIGARETTE_NOTIFICATION_FORM;
import static se.sundsvall.aotloader.service.model.JsonParameterType.FOLKOL_SERVING_NOTIFICATION_FORM;
import static se.sundsvall.aotloader.source.AssetContents.FIELD_MUNICIPALITY_ID;
import static se.sundsvall.aotloader.source.FilePurposes.BUSINESS_DESCRIPTION;
import static se.sundsvall.aotloader.source.FilePurposes.POWER_OF_ATTORNEY;
import static se.sundsvall.aotloader.source.FilePurposes.SELF_MONITORING_PROGRAM;

/**
 * Maps a candidate to the errand data (Ärendeuppgifter) of its errand: the form of its errand type, which Draken picks
 * by the label of the type and shows under the name of its schema (decisions 98 and 102). An inspection errand or visit
 * gets the place it concerns, as Draken fills the form when it starts an inspection of a place. A notification gets
 * what the source gives, and the answers decided for a migrated notification where the source has none. Applications
 * and documentation errands get no form.
 */
public final class ErrandFormMapper {

	/** The answer to a question the form requires a text for and the source has no answer to (decision 102). */
	private static final String NOT_IN_SOURCE = "Uppgift saknas – migrerat ärende";

	private static final String YES = "YES";
	private static final String NO = "NO";
	private static final String UPLOAD = "UPLOAD";
	private static final String DESCRIBE = "DESCRIBE";
	private static final String INDEFINITE = "INDEFINITE";
	private static final String SELF_MONITORING_SHOWN_AT_INSPECTION = "NO_CAN_SHOW_AT_INSPECTION";
	private static final String CAN_SHOW_DOCUMENTATION = "CAN_SHOW_DOCUMENTATION";
	private static final String CAN_SHOW_FOOD_REGISTRATION = "CAN_SHOW_FOOD_REGISTRATION";
	// What an address or a name is compared without: case, punctuation and blanks, also the no-break space, which \s alone
	// does not match
	private static final Pattern NOT_COMPARED = Pattern.compile("[\\s\\p{Z}.,-]");

	private final AotLoaderProperties properties;

	ErrandFormMapper(final AotLoaderProperties properties) {
		this.properties = properties;
	}

	/**
	 * @param  labelPaths the label paths of an errand, or of the settings of an errand type
	 * @return            the forms whose type label is among them, in the order of {@link JsonParameterType}
	 */
	public static List<JsonParameterType> formsOf(final Collection<String> labelPaths) {
		final var labels = labelPaths.stream().map(LabelPaths::normalize).collect(Collectors.toSet());
		return Stream.of(JsonParameterType.values())
			.filter(type -> type.formLabel() != null && labels.contains(LabelPaths.normalize(type.formLabel())))
			.toList();
	}

	/**
	 * @param  candidate      the candidate
	 * @param  municipalityId the municipality the run is started for
	 * @return                the form of the errand type, with the municipality of the place where the form has it; empty
	 *                        when the errand type has no form the migration fills, or the source lacks something the form
	 *                        requires, which {@link #reportLines} then tells
	 */
	Optional<JsonContent> toForm(final Candidate candidate, final String municipalityId) {
		return build(candidate).filter(form -> form.missing().isEmpty()).map(form -> {
			final var value = form.value();
			if (form.placeField() != null) {
				final var place = value.get(form.placeField()) instanceof final ObjectNode found ? found : value.putObject(form.placeField());
				place.put(FIELD_MUNICIPALITY_ID, properties.premisesMunicipalityOf(candidate.place(), municipalityId));
			}
			return new JsonContent(form.type(), value);
		});
	}

	/**
	 * @return the lines the report tells of the errand data, without any value: why the errand gets none although its type
	 *         has a form, and what the source has but the form leaves out, since it does not follow the schema; none when
	 *         there is nothing to tell
	 */
	public static List<String> reportLines(final Candidate candidate) {
		return build(candidate).map(form -> {
			final var lines = new ArrayList<String>();
			if (!form.missing().isEmpty()) {
				lines.add("Inga ärendeuppgifter, eftersom formuläret kräver det som saknas: " + String.join("; ", form.missing()));
			}
			if (!form.omitted().isEmpty()) {
				lines.add("Utelämnat i ärendeuppgifterna, eftersom det inte följer schemat: " + String.join("; ", form.omitted()));
			}
			return List.copyOf(lines);
		}).orElse(List.of());
	}

	/**
	 * @return                       the form of the first type label of a form among the labels of the errand, empty when
	 *                               it has none
	 * @throws IllegalStateException for a form that has a type label but is not mapped here
	 */
	private static Optional<Form> build(final Candidate candidate) {
		return formsOf(candidate.errand().labelPaths()).stream().findFirst().flatMap(type -> switch (type) {
			case INTERNAL_INSPECTION_FORM, EXTERNAL_INSPECTION_FORM -> inspection(type, candidate);
			case FOLKOL_SALES_NOTIFICATION_FORM, FOLKOL_SERVING_NOTIFICATION_FORM -> Optional.of(folkol(type, candidate));
			case ECIGARETTE_NOTIFICATION_FORM, TOBACCO_FREE_NICOTINE_NOTIFICATION_FORM -> Optional.of(tobaccoProduct(type, candidate));
			default -> throw new IllegalStateException("The form " + type + " has a type label but is not mapped");
		});
	}

	/**
	 * The form of an inspection errand: the place by the field the schema of its kind of place has, and nothing else. An
	 * errand without a place gets none.
	 */
	private static Optional<Form> inspection(final JsonParameterType type, final Candidate candidate) {
		if (candidate.place() == null) {
			return Optional.empty();
		}
		final var schema = candidate.type().getPlaceSchema();
		final var contents = new AssetContents(schema).place(candidate.place());
		return Optional.of(new Form(type, contents.fields().toNode(), schema.placeField(), contents.omitted(), List.of()));
	}

	/**
	 * The form of a notification of low-alcohol beer (decision 107): permanent sales of food for sales and a registration
	 * as a food business for serving, which a notification needs to be made and the schema stops one without, both
	 * documented at inspection, as the source does not tell; no beer made on the premises; and the self-monitoring
	 * programme attached when the errand has it as a file, or else shown at inspection.
	 */
	private static Form folkol(final JsonParameterType type, final Candidate candidate) {
		final var form = new NotificationForm(candidate);
		form.applicant();
		if (type == FOLKOL_SERVING_NOTIFICATION_FORM) {
			form.answer("isFoodRegistered", YES)
				.answers("canShowFoodRegistrationAtInspection", CAN_SHOW_FOOD_REGISTRATION);
		} else {
			form.answer("hasPermanentFoodSales", YES)
				.answer("producesLowAlcoholBeerOnPremises", NO)
				.answers("canShowDocumentationAtInspection", CAN_SHOW_DOCUMENTATION);
		}
		form.answer("selfMonitoringProgram", form.hasFile(SELF_MONITORING_PROGRAM) ? YES : SELF_MONITORING_SHOWN_AT_INSPECTION);
		form.place();
		return form.result(type);
	}

	/**
	 * The form of a notification of e-cigarettes or tobacco-free nicotine products: sales until further notice from the
	 * day of the notification, the phone of the sales place, which the form requires, and no distance selling, supplier
	 * agreement or particularly dangerous products, as the source does not tell.
	 */
	private static Form tobaccoProduct(final JsonParameterType type, final Candidate candidate) {
		final var form = new NotificationForm(candidate);
		form.applicant();
		form.salesPeriod();
		form.place();
		form.requirePlacePhone();
		form.answer("includesDistanceSelling", NO);
		if (type == ECIGARETTE_NOTIFICATION_FORM) {
			form.answer("includesParticularlyDangerousProducts", NO);
		}
		form.answer("hasSupplierAgreement", NO);
		return form.result(type);
	}

	/**
	 * The form of an errand while it is built.
	 *
	 * @param type       the form
	 * @param value      what it holds, without the municipality of the place
	 * @param placeField the field of the place that gets the municipality, null for a form without one
	 * @param omitted    what the source has but the form leaves out, as the path of the field and why
	 * @param missing    what the form requires and the source lacks, which leaves the errand without the form
	 */
	private record Form(JsonParameterType type, ObjectNode value, String placeField, List<String> omitted, List<String> missing) {
	}

	/**
	 * A street address as the forms want it: the postal code without blanks.
	 */
	private record Address(String street, String postalCode, String city) {

		private static final Address NONE = new Address(null, null, null);

		static Address of(final String street, final String postalCode, final String city) {
			return new Address(Texts.trimToNull(street), Texts.withoutBlanks(postalCode), Texts.trimToNull(city));
		}

		boolean isEmpty() {
			return street == null && postalCode == null && city == null;
		}

		/**
		 * @return why the address cannot be given in a form, which wants street, postal code and city, null when it can
		 */
		String fault(final String what) {
			if (street == null || postalCode == null || city == null) {
				return what + " saknar gata, postnummer eller ort";
			}
			return FieldForms.isPostalCode(postalCode) ? null : what + " har ett postnummer utan fem siffror";
		}

		boolean sameAs(final Address other) {
			return comparable(street).equals(comparable(other.street)) && comparable(postalCode).equals(comparable(other.postalCode))
				&& comparable(city).equals(comparable(other.city));
		}

		/**
		 * @return the fields, with the street, postal code and city added
		 */
		JsonFields addTo(final JsonFields fields) {
			return fields.text("street", street)
				.text("postalCode", postalCode)
				.text("city", city);
		}
	}

	private static String comparable(final String value) {
		return value == null ? "" : NOT_COMPARED.matcher(Normalizer.normalize(value, Normalizer.Form.NFC)).replaceAll("").toUpperCase(Locale.ROOT);
	}

	/**
	 * The answers of a notification form, in the order of the schemas, and what leaves the errand without the form.
	 */
	private static final class NotificationForm {

		private final Candidate candidate;
		private final Holder holder;
		private final JsonFields fields = JsonFields.object();
		private final Omissions omissions = new Omissions();
		private final List<String> missing = new ArrayList<>();

		private NotificationForm(final Candidate candidate) {
			this.candidate = candidate;
			this.holder = candidate.holder();
		}

		NotificationForm answer(final String question, final String answer) {
			fields.text(question, answer);
			return this;
		}

		NotificationForm answers(final String question, final String... answers) {
			fields.texts(question, List.of(answers));
			return this;
		}

		/**
		 * The answers of the applicant that every notification form asks first: whether the applicant signs for the company,
		 * which a power of attorney among the files says it does not, where the invoices go, and the description of the
		 * business, attached when the errand has it as a file.
		 */
		void applicant() {
			answer("isAuthorizedSignatory", hasFile(POWER_OF_ATTORNEY) ? NO : YES);
			invoicing();
			if (hasFile(BUSINESS_DESCRIPTION)) {
				answer("businessDescriptionChoice", UPLOAD);
			} else {
				answer("businessDescriptionChoice", DESCRIBE)
					.answer("businessDescription", NOT_IN_SOURCE);
			}
		}

		/**
		 * Where the invoices go (decision 101): to the company when the place has no invoice address or the one of the
		 * company, and else to the invoice address, in care of the recipient when it is another than the company. The person
		 * the invoices are marked for is the reference.
		 */
		private void invoicing() {
			final var invoicing = candidate.place() == null ? null : candidate.place().invoicing();
			final var address = invoicing == null ? Address.NONE : Address.of(invoicing.address(), invoicing.zipCode(), invoicing.city());
			final var fault = address.isEmpty() ? null : address.fault("fakturaadressen");
			if (fault != null) {
				missing.add(fault);
			} else if (address.isEmpty() || address.sameAs(holderAddress())) {
				answer("invoiceAddressSameAsCompany", YES);
			} else {
				answer("invoiceAddressSameAsCompany", NO);
				fields.object("invoiceRecipient", address.addTo(JsonFields.object().text("careOf", otherThanHolder(invoicing.name()))));
			}
			answer("invoiceReference", invoicing == null ? null : invoicing.contactPerson());
		}

		/**
		 * Sales until further notice, from the day of the notification.
		 */
		void salesPeriod() {
			final var notified = candidate.asset() == null ? null : candidate.asset().issued();
			if (notified == null) {
				missing.add("anmälan saknar datum");
			}
			answer("salesPeriod", INDEFINITE);
			fields.date("salesStartDate", notified);
		}

		/**
		 * The place, by the field the schema of its kind of place has, premises or pointOfSale, which the fields of the place
		 * begin with: its name, whether its address is the one of the company, else the address, and its phone and email
		 * address.
		 */
		void place() {
			final var place = candidate.place();
			if (place == null) {
				missing.add("ärendet saknar ställe");
				return;
			}
			final var field = candidate.type().getPlaceSchema().placeField();
			if (Texts.isBlank(place.name())) {
				missing.add("stället saknar namn");
			}
			answer(field + "Name", place.name());
			final var address = Address.of(place.address(), place.zipCode(), place.city());
			final var fault = address.fault("ställets adress");
			if (fault != null) {
				missing.add(fault);
			} else if (address.sameAs(holderAddress())) {
				answer(field + "AddressSameAsCompany", YES);
			} else {
				answer(field + "AddressSameAsCompany", NO);
				fields.object(field + "Address", address.addTo(JsonFields.object()));
			}
			fields.object(field + "Contact", JsonFields.object()
				.text("phoneNumber", place.phone())
				.text("emailAddress", omissions.emailAddress(field + "Contact.emailAddress", place.email())));
		}

		/**
		 * Notes the phone of the place as missing when it has none, for a form that requires it.
		 */
		void requirePlacePhone() {
			if (candidate.place() != null && Texts.isBlank(candidate.place().phone())) {
				missing.add("stället saknar telefon");
			}
		}

		boolean hasFile(final String purpose) {
			return candidate.files().details().values().stream().anyMatch(details -> purpose.equals(details.purpose()));
		}

		Form result(final JsonParameterType type) {
			return new Form(type, fields.toNode(), null, omissions.list(), List.copyOf(missing));
		}

		private Address holderAddress() {
			return holder == null ? Address.NONE : Address.of(holder.address(), holder.zipCode(), holder.city());
		}

		/**
		 * @return the name, unless it is the one of the holder or blank
		 */
		private String otherThanHolder(final String name) {
			if (Texts.isBlank(name) || holder != null && comparable(name).equals(comparable(holder.name()))) {
				return null;
			}
			return name;
		}
	}
}
