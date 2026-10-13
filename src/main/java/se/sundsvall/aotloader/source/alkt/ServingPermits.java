package se.sundsvall.aotloader.source.alkt;

import java.util.ArrayList;
import java.util.List;
import se.sundsvall.aotloader.service.model.ServingPermitSubtype;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PermitRow;

/**
 * Reads the markings of a serving permit: its subtype (section 7.3.4), and the drinks, guests and scope it covers as
 * the texts of the errand's parameters (section 7.3.3).
 */
final class ServingPermits {

	private ServingPermits() {}

	/**
	 * @return the subtype of the permit, by the first rule that holds; null when none does
	 */
	static ServingPermitSubtype subtype(final PermitRow permit, final PermitPeriods periods) {
		if (!permit.aretRunt() && periods.hasDatePeriod()) {
			return permit.servingPublic() ? ServingPermitSubtype.TEMPORARY_SERVING_PUBLIC : ServingPermitSubtype.TEMPORARY_SERVING_PRIVATE;
		}
		if (permit.servingPublic() || permit.servingPrivate()) {
			return ServingPermitSubtype.PERMANENT_SERVING;
		}
		if (permit.catering()) {
			return ServingPermitSubtype.PERMANENT_CATERING;
		}
		if (permit.gardsforsaljning()) {
			return ServingPermitSubtype.FARM_SALES;
		}
		if (permit.provsmakning()) {
			return ServingPermitSubtype.TASTING;
		}
		return null;
	}

	static List<String> drinks(final PermitRow permit) {
		final var drinks = new ArrayList<String>();
		addIf(drinks, permit.starkol(), "Starköl");
		addIf(drinks, permit.vin(), "Vin");
		addIf(drinks, permit.spritdrycker(), "Spritdrycker");
		addIf(drinks, permit.ajaDrycker(), "Andra jästa alkoholdrycker");
		addIf(drinks, permit.alp(), "Alkoholdrycksliknande preparat");
		return drinks;
	}

	static List<String> servingTo(final PermitRow permit) {
		final var servingTo = new ArrayList<String>();
		addIf(servingTo, permit.servingPublic(), "Allmänheten");
		addIf(servingTo, permit.servingPrivate(), "Slutna sällskap");
		return servingTo;
	}

	static List<String> scope(final PermitRow permit) {
		final var scope = new ArrayList<String>();
		addIf(scope, permit.uteservering(), "Uteservering");
		addIf(scope, permit.catering(), "Catering");
		addIf(scope, permit.provsmakning(), "Provsmakning");
		addIf(scope, permit.gardsforsaljning(), "Gårdsförsäljning");
		addIf(scope, permit.kryddning(), "Egen kryddning");
		addIf(scope, permit.roomservice(), "Rumsservering");
		addIf(scope, permit.minibar(), "Minibar");
		addIf(scope, permit.pausservering(), "Pausservering");
		addIf(scope, permit.trafikservering(), "Trafikservering");
		return scope;
	}

	private static void addIf(final List<String> list, final boolean condition, final String value) {
		if (condition) {
			list.add(value);
		}
	}
}
