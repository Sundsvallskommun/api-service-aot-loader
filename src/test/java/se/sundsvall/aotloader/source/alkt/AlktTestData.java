package se.sundsvall.aotloader.source.alkt;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import se.sundsvall.aotloader.source.ContactRow;
import se.sundsvall.aotloader.source.PersonRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ConditionRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.DecisionRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ErrandRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.FileRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PermitRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PlaceRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ServingTime;

/**
 * Made up rows of AlkT for the tests. No row holds real data.
 */
final class AlktTestData {

	private AlktTestData() {}

	static PlaceRow place(final int objektId, final Integer agarId, final String restaurantNumber) {
		return new PlaceRow(objektId, agarId, restaurantNumber, "12", "Restaurang " + objektId, "Storgatan " + objektId, "85230", "Sundsvall", "060-123456", "info" + objektId + "@example.com", "01", "ritning.pdf", null);
	}

	static ContactRow owner(final int agarId, final String number) {
		return new ContactRow(agarId, number, "Bolaget " + agarId + " AB", "Kontakt Person", "Box 1", "Våning 2", "85230", "Sundsvall", "060-654321", "bolag" + agarId + "@example.com");
	}

	static ContactRow applicant(final int sokandeId, final String number) {
		return new ContactRow(sokandeId, number, "Sökande " + sokandeId + " AB", null, "Gata 1", null, "85230", "Sundsvall", null, null);
	}

	static Map<String, Map<String, String>> codeTexts() {
		final var texts = new HashMap<String, Map<String, String>>();
		texts.put("A", Map.of("07", "Tillsyn, ifrågasatt åtgärd", "08", "Tillsyn, restaurangrapport"));
		texts.put("B", Map.of("GT", "Serveringstillstånd", "ER", "Erinran"));
		texts.put("G", Map.of("01", "Centrum"));
		texts.put("O", Map.of("0100", "Krav på bordsservering"));
		texts.put("P", Map.of("VD", "Verkställande direktör"));
		texts.put("Q", Map.of("12", "Kvartersrestaurang", "UNG", "Ungdomlig målgrupp"));
		return texts;
	}

	static SnapshotBuilder snapshot() {
		return new SnapshotBuilder();
	}

	static PermitBuilder permit(final int objektId) {
		return new PermitBuilder(objektId);
	}

	static final class SnapshotBuilder {

		private final List<PermitRow> permits = new ArrayList<>();
		private final Map<Integer, PlaceRow> places = new HashMap<>();
		private final Map<Integer, ContactRow> owners = new HashMap<>();
		private final Map<Integer, List<ConditionRow>> conditions = new HashMap<>();
		private final Map<Integer, DecisionRow> decisions = new HashMap<>();
		private final Map<Integer, ErrandRow> errands = new HashMap<>();
		private final Map<Integer, List<FileRow>> files = new HashMap<>();
		private final Map<Integer, List<PersonRow>> persons = new HashMap<>();
		private final Map<Integer, ContactRow> applicants = new HashMap<>();

		SnapshotBuilder permit(final PermitRow row) {
			permits.add(row);
			return this;
		}

		SnapshotBuilder place(final PlaceRow row) {
			places.put(row.objektId(), row);
			return this;
		}

		SnapshotBuilder owner(final ContactRow row) {
			owners.put(row.id(), row);
			return this;
		}

		SnapshotBuilder condition(final ConditionRow row) {
			conditions.computeIfAbsent(row.objektId(), _ -> new ArrayList<>()).add(row);
			return this;
		}

		SnapshotBuilder decision(final DecisionRow row) {
			decisions.put(row.beslutsId(), row);
			return this;
		}

		SnapshotBuilder errand(final ErrandRow row) {
			errands.put(row.arendeId(), row);
			return this;
		}

		SnapshotBuilder file(final FileRow row) {
			files.computeIfAbsent(row.arendeId(), _ -> new ArrayList<>()).add(row);
			return this;
		}

		SnapshotBuilder person(final PersonRow row) {
			persons.computeIfAbsent(row.agarId(), _ -> new ArrayList<>()).add(row);
			return this;
		}

		SnapshotBuilder applicant(final ContactRow row) {
			applicants.put(row.id(), row);
			return this;
		}

		AlktSnapshot build() {
			return new AlktSnapshot(permits, places, owners, conditions, decisions, errands, files, persons, applicants, codeTexts());
		}
	}

	static final class PermitBuilder {

		private final int objektId;
		private Integer gtAgarId;
		private boolean starkol = true;
		private boolean vin = true;
		private boolean sprit;
		private boolean aja;
		private boolean alp;
		private boolean aretRunt = true;
		private String arligStart;
		private String arligSlut;
		private String periodStart;
		private String periodSlut;
		private boolean uteservering;
		private boolean catering;
		private boolean provsmakning;
		private boolean gardsforsaljning;
		private boolean servingPublic = true;
		private boolean servingPrivate;
		private final List<ServingTime> times = new ArrayList<>(List.of(new ServingTime("11:00", "01:00")));
		private LocalDate issued = LocalDate.of(2019, 5, 1);
		private String diaryNumber;
		private String gtRestaurantNumber;
		private Integer beslutsId;

		private PermitBuilder(final int objektId) {
			this.objektId = objektId;
			this.diaryNumber = "IAN-2019-" + objektId;
		}

		PermitBuilder gtAgarId(final Integer value) {
			gtAgarId = value;
			return this;
		}

		PermitBuilder drinks(final boolean starkolValue, final boolean vinValue, final boolean spritValue, final boolean ajaValue, final boolean alpValue) {
			starkol = starkolValue;
			vin = vinValue;
			sprit = spritValue;
			aja = ajaValue;
			alp = alpValue;
			return this;
		}

		PermitBuilder aretRunt(final boolean value) {
			aretRunt = value;
			return this;
		}

		PermitBuilder season(final String start, final String end) {
			arligStart = start;
			arligSlut = end;
			return this;
		}

		PermitBuilder period(final String start, final String end) {
			periodStart = start;
			periodSlut = end;
			return this;
		}

		PermitBuilder scope(final boolean uteserveringValue, final boolean cateringValue, final boolean provsmakningValue, final boolean gardsforsaljningValue) {
			uteservering = uteserveringValue;
			catering = cateringValue;
			provsmakning = provsmakningValue;
			gardsforsaljning = gardsforsaljningValue;
			return this;
		}

		PermitBuilder servingTo(final boolean publicValue, final boolean privateValue) {
			servingPublic = publicValue;
			servingPrivate = privateValue;
			return this;
		}

		PermitBuilder time(final String start, final String end) {
			times.add(new ServingTime(start, end));
			return this;
		}

		PermitBuilder issued(final LocalDate value) {
			issued = value;
			return this;
		}

		PermitBuilder diaryNumber(final String value) {
			diaryNumber = value;
			return this;
		}

		PermitBuilder gtRestaurantNumber(final String value) {
			gtRestaurantNumber = value;
			return this;
		}

		PermitBuilder beslutsId(final Integer value) {
			beslutsId = value;
			return this;
		}

		PermitRow build() {
			final var servingTimes = new ArrayList<>(times);
			while (servingTimes.size() < 8) {
				servingTimes.add(new ServingTime(null, null));
			}
			return new PermitRow(objektId, gtAgarId, starkol, vin, sprit, aja, alp, aretRunt, arligStart, arligSlut, periodStart, periodSlut, false, uteservering, false, false, false, catering, provsmakning, false,
				gardsforsaljning, servingPublic, servingPrivate, servingTimes, "200", "80", "Matsal", List.of("En notering"), LocalDate.of(2015, 1, 1), "SN 2015-1", issued, diaryNumber, gtRestaurantNumber, beslutsId);
		}
	}
}
