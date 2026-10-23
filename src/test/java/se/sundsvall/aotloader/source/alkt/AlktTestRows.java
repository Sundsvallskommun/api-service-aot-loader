package se.sundsvall.aotloader.source.alkt;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import se.sundsvall.aotloader.service.model.FileDetails;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.DecisionRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ErrandRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.FileRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.TextRow;

/**
 * Builds rows of AlkT for the tests, with the columns a test does not need left empty.
 */
public final class AlktTestRows {

	private AlktTestRows() {}

	/**
	 * @return an errand without notes, handlers or the day it was registered
	 */
	public static ErrandRow errand(final Integer arendeId, final Integer objektId, final String arendeTyp, final String diarieNr, final LocalDate oppnad, final LocalDate avslutad,
		final Integer sokandeId) {
		return errand(arendeId, objektId, arendeTyp, diarieNr, oppnad, avslutad, sokandeId, List.of());
	}

	/**
	 * @return an errand without handlers or the day it was registered
	 */
	public static ErrandRow errand(final Integer arendeId, final Integer objektId, final String arendeTyp, final String diarieNr, final LocalDate oppnad, final LocalDate avslutad,
		final Integer sokandeId, final List<String> notes) {
		return errand(arendeId, objektId, arendeTyp, diarieNr, oppnad, avslutad, sokandeId, notes, List.of());
	}

	/**
	 * @return an errand without the day it was registered
	 */
	public static ErrandRow errand(final Integer arendeId, final Integer objektId, final String arendeTyp, final String diarieNr, final LocalDate oppnad, final LocalDate avslutad,
		final Integer sokandeId, final List<String> notes, final List<String> handlaggare) {
		return new ErrandRow(arendeId, objektId, arendeTyp, diarieNr, oppnad, avslutad, sokandeId, notes, handlaggare, null);
	}

	/**
	 * @return a decision without a text or a decision maker
	 */
	public static DecisionRow decision(final Integer beslutsId, final Integer arendeId, final String beslutsTyp, final LocalDateTime beslutad, final LocalDate giltigtTom, final String mall,
		final String worddokument, final String bifogadfil) {
		return decision(beslutsId, arendeId, beslutsTyp, beslutad, giltigtTom, mall, worddokument, bifogadfil, null);
	}

	/**
	 * @return a decision without a decision maker
	 */
	public static DecisionRow decision(final Integer beslutsId, final Integer arendeId, final String beslutsTyp, final LocalDateTime beslutad, final LocalDate giltigtTom, final String mall,
		final String worddokument, final String bifogadfil, final String beslutsText) {
		return new DecisionRow(beslutsId, arendeId, beslutsTyp, beslutad, giltigtTom, mall, worddokument, bifogadfil, beslutsText, null);
	}

	/**
	 * @return an event or picture that is not marked as confidential and has no diary number of its own
	 */
	public static TextRow text(final LocalDateTime tidpunkt, final String handelseTyp, final boolean picture, final String text) {
		return text(tidpunkt, handelseTyp, picture, text, false);
	}

	/**
	 * @return an event or picture without a diary number of its own
	 */
	public static TextRow text(final LocalDateTime tidpunkt, final String handelseTyp, final boolean picture, final String text, final boolean confidential) {
		return new TextRow(tidpunkt, handelseTyp, picture, text, confidential, null);
	}

	/**
	 * @return a file without a purpose or a date
	 */
	public static FileRow file(final Integer arendeId, final String name, final boolean generated) {
		return new FileRow(arendeId, name, generated, FileDetails.NONE);
	}
}
