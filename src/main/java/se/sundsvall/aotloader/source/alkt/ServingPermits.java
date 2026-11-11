package se.sundsvall.aotloader.source.alkt;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import se.sundsvall.aotloader.service.model.ServingPermitSubtype;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PermitRow;

/**
 * Reads the markings of a serving permit: its subtype (section 7.3.4), its last day, the drinks, guests and scope it
 * covers as the texts of the errand's parameters (section 7.3.3), and its beverages as the alcohol schema has them.
 */
final class ServingPermits {

	/** The alcohol-like preparations of the alcohol schema. */
	private static final String ALCOHOL_LIKE_PREPARATIONS = "ALCOHOLIC_BEVERAGE_LIKE_PREPARATIONS";

	private ServingPermits() {}

	/**
	 * @return the subtype of the permit, by the first rule that holds; null when none does
	 */
	static ServingPermitSubtype subtype(final PermitRow permit, final PermitPeriods periods) {
		if (!permit.period().aretRunt() && periods.hasDatePeriod()) {
			return permit.servingPublic() ? ServingPermitSubtype.TEMPORARY_SERVING_PUBLIC : ServingPermitSubtype.TEMPORARY_SERVING_PRIVATE;
		}
		if (permit.servingPublic() || permit.servingPrivate()) {
			return ServingPermitSubtype.PERMANENT_SERVING;
		}
		if (permit.scope().catering()) {
			return ServingPermitSubtype.PERMANENT_CATERING;
		}
		if (permit.scope().gardsforsaljning()) {
			return ServingPermitSubtype.FARM_SALES;
		}
		if (permit.scope().provsmakning()) {
			return ServingPermitSubtype.TASTING;
		}
		return null;
	}

	/**
	 * @return the last day of a permit that holds for a period of dates and not all year, null for any other
	 */
	static LocalDate validTo(final PermitRow permit, final PermitPeriods periods) {
		return !permit.period().aretRunt() && periods.hasDatePeriod() ? periods.periodEnd() : null;
	}

	static List<String> drinks(final PermitRow permit) {
		final var drinks = new ArrayList<String>();
		addIf(drinks, permit.drinks().starkol(), "Starköl");
		addIf(drinks, permit.drinks().vin(), "Vin");
		addIf(drinks, permit.drinks().spritdrycker(), "Spritdrycker");
		addIf(drinks, permit.drinks().ajaDrycker(), "Andra jästa alkoholdrycker");
		addIf(drinks, permit.drinks().alp(), "Alkoholdrycksliknande preparat");
		return drinks;
	}

	/**
	 * @return the beverages the permit covers, as the constants of the alcohol schema, in its order
	 */
	static List<String> beverages(final PermitRow permit) {
		final var beverages = new ArrayList<String>();
		addIf(beverages, permit.drinks().spritdrycker(), "SPIRITS");
		addIf(beverages, permit.drinks().vin(), "WINE");
		addIf(beverages, permit.drinks().starkol(), "STRONG_BEER");
		addIf(beverages, permit.drinks().ajaDrycker(), "OTHER_FERMENTED_BEVERAGES");
		addIf(beverages, permit.drinks().alp(), ALCOHOL_LIKE_PREPARATIONS);
		return beverages;
	}

	static List<String> servingTo(final PermitRow permit) {
		final var servingTo = new ArrayList<String>();
		addIf(servingTo, permit.servingPublic(), "Allmänheten");
		addIf(servingTo, permit.servingPrivate(), "Slutna sällskap");
		return servingTo;
	}

	static List<String> scope(final PermitRow permit) {
		final var scope = new ArrayList<String>();
		addIf(scope, permit.scope().uteservering(), "Uteservering");
		addIf(scope, permit.scope().catering(), "Catering");
		addIf(scope, permit.scope().provsmakning(), "Provsmakning");
		addIf(scope, permit.scope().gardsforsaljning(), "Gårdsförsäljning");
		addIf(scope, permit.scope().kryddning(), "Egen kryddning");
		addIf(scope, permit.scope().roomservice(), "Rumsservering");
		addIf(scope, permit.scope().minibar(), "Minibar");
		addIf(scope, permit.scope().pausservering(), "Pausservering");
		addIf(scope, permit.scope().trafikservering(), "Trafikservering");
		return scope;
	}

	private static void addIf(final List<String> list, final boolean condition, final String value) {
		if (condition) {
			list.add(value);
		}
	}
}
