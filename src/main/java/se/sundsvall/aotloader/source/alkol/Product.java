package se.sundsvall.aotloader.source.alkol;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.source.CodeTexts;
import se.sundsvall.aotloader.source.PermitFiles;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.EventRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.SalesPlaceRow;

/**
 * The products that are notified, with the errand types and event types that register a notification, and the event
 * types and templates of the letters that confirm one (section 7.4, decisions 21 and 38).
 */
enum Product {

	FOLKOL("FOLKOL", "Folköl", Set.of("Fföl", "SÖL"), Set.of("014", "015"), Set.of("BeaFol"), Set.of("BEKFOL", "MOTFOL"), Set.of("SÖL", "015")),
	ECIGARETTER("ECIGARETTER", "E-cigaretter och påfyllningsbehållare", Set.of("ec"), Set.of("105"), Set.of("BekAnm"), Set.of("BEKECI"), Set.of()),
	TOBAKSFRI_NIKOTIN("TOBAKSFRI_NIKOTIN", "Tobaksfria nikotinprodukter", Set.of("tn"), Set.of("047"), Set.of(), Set.of("MTN"), Set.of());

	private final String code;
	private final String parameterText;
	private final Set<String> errandTypes;
	private final Set<String> registrationEvents;
	private final Set<String> confirmationEvents;
	private final Set<String> confirmationTemplates;
	private final Set<String> servingCodes;

	/**
	 * @param confirmationTemplates the templates of the letters that confirm a notification of the product, in upper
	 *                              case: the confirmations and the receipts (decision 38)
	 */
	Product(final String code, final String parameterText, final Set<String> errandTypes, final Set<String> registrationEvents, final Set<String> confirmationEvents,
		final Set<String> confirmationTemplates, final Set<String> servingCodes) {
		this.code = code;
		this.parameterText = parameterText;
		this.errandTypes = errandTypes;
		this.registrationEvents = registrationEvents;
		this.confirmationEvents = confirmationEvents;
		this.confirmationTemplates = confirmationTemplates;
		this.servingCodes = servingCodes;
	}

	/**
	 * @return the products the sales place is marked for
	 */
	static List<Product> notifiedOn(final SalesPlaceRow place) {
		return Arrays.stream(values()).filter(product -> product.isNotifiedOn(place)).toList();
	}

	/**
	 * @return true when the sales place is marked for any product
	 */
	static boolean hasNotification(final SalesPlaceRow place) {
		return Arrays.stream(values()).anyMatch(product -> product.isNotifiedOn(place));
	}

	/**
	 * @return true for an event that registers a product the place has a notification of; its files go to that
	 *         notification
	 */
	static boolean registersNotification(final SalesPlaceRow place, final EventRow event) {
		return place != null && !place.upphort() && Arrays.stream(values())
			.anyMatch(product -> product.isNotifiedOn(place) && product.concerns(event));
	}

	/**
	 * @return the code of the product in the id of the notification, e.g. 1234-FOLKOL; it must never change once a
	 *         notification has been migrated
	 */
	String code() {
		return code;
	}

	String parameterText() {
		return parameterText;
	}

	boolean isNotifiedOn(final SalesPlaceRow place) {
		return switch (this) {
			case FOLKOL -> place.folkol();
			case ECIGARETTER -> place.ecigaretter();
			case TOBAKSFRI_NIKOTIN -> place.tobaksfriNikotin();
		};
	}

	boolean isErrandType(final String code) {
		return CodeTexts.isOneOf(code, errandTypes);
	}

	/**
	 * @return true for an event that registers a notification of the product
	 */
	boolean isRegistration(final EventRow event) {
		return CodeTexts.isOneOf(event.handelseTyp(), registrationEvents);
	}

	/**
	 * @return true for an event that registers or confirms a notification of the product. A letter made from the template
	 *         of a confirmation is of the product of that template, whatever the type of its event; any other event
	 *         confirms by its type.
	 */
	boolean concerns(final EventRow event) {
		if (isRegistration(event)) {
			return true;
		}
		return ofTemplate(event.mall()).map(product -> product == this).orElseGet(() -> CodeTexts.isOneOf(event.handelseTyp(), confirmationEvents));
	}

	boolean isConfirmationTemplate(final String template) {
		return PermitFiles.isTemplate(template, confirmationTemplates);
	}

	private static Optional<Product> ofTemplate(final String template) {
		return Arrays.stream(values()).filter(product -> product.isConfirmationTemplate(template)).findFirst();
	}

	/**
	 * @return true for an errand type or event type that notifies serving rather than sales of the product
	 */
	boolean isServingCode(final String code) {
		return CodeTexts.isOneOf(code, servingCodes);
	}

	ErrandType errandType(final boolean serving) {
		return switch (this) {
			case FOLKOL -> serving ? ErrandType.FOLKOL_SERVING_NOTIFICATION : ErrandType.FOLKOL_SALES_NOTIFICATION;
			case ECIGARETTER -> ErrandType.ECIGARETTE_NOTIFICATION;
			case TOBAKSFRI_NIKOTIN -> ErrandType.TOBACCO_FREE_NICOTINE_NOTIFICATION;
		};
	}
}
