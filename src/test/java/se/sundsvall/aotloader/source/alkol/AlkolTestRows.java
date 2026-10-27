package se.sundsvall.aotloader.source.alkol;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.DecisionRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.ErrandRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.EventRow;

/**
 * Builds rows of AlkOL for the tests, with the columns a test does not need left empty.
 */
public final class AlkolTestRows {

	private AlkolTestRows() {}

	/**
	 * @return an errand without notes, handlers or the day it was registered
	 */
	public static ErrandRow errand(final Integer arendeId, final Integer fsId, final String arendeTyp, final String diarieNr, final LocalDate oppnad, final LocalDate avslutad,
		final Integer sokandeId) {
		return errand(arendeId, fsId, arendeTyp, diarieNr, oppnad, avslutad, sokandeId, null);
	}

	/**
	 * @return an errand without handlers or the day it was registered
	 */
	public static ErrandRow errand(final Integer arendeId, final Integer fsId, final String arendeTyp, final String diarieNr, final LocalDate oppnad, final LocalDate avslutad,
		final Integer sokandeId, final String noteringar) {
		return errand(arendeId, fsId, arendeTyp, diarieNr, oppnad, avslutad, sokandeId, noteringar, List.of());
	}

	/**
	 * @return an errand without the day it was registered
	 */
	public static ErrandRow errand(final Integer arendeId, final Integer fsId, final String arendeTyp, final String diarieNr, final LocalDate oppnad, final LocalDate avslutad,
		final Integer sokandeId, final String noteringar, final List<String> handlaggare) {
		return new ErrandRow(arendeId, fsId, arendeTyp, diarieNr, oppnad, avslutad, sokandeId, noteringar, handlaggare, null);
	}

	/**
	 * @return a decision without a text or a decision maker
	 */
	public static DecisionRow decision(final Integer beslutsId, final Integer arendeId, final String beslutsTyp, final LocalDateTime beslutad, final LocalDate giltigtFrom, final String mall,
		final String worddokument, final String bifogadfil) {
		return decision(beslutsId, arendeId, beslutsTyp, beslutad, giltigtFrom, mall, worddokument, bifogadfil, null);
	}

	/**
	 * @return a decision without a decision maker
	 */
	public static DecisionRow decision(final Integer beslutsId, final Integer arendeId, final String beslutsTyp, final LocalDateTime beslutad, final LocalDate giltigtFrom, final String mall,
		final String worddokument, final String bifogadfil, final String beslutsText) {
		return new DecisionRow(beslutsId, arendeId, beslutsTyp, beslutad, giltigtFrom, null, mall, worddokument, bifogadfil, beslutsText, null);
	}

	/**
	 * @return an event without a text, not marked as confidential
	 */
	public static EventRow event(final Integer handelseId, final Integer fsId, final Integer arendeId, final String handelseTyp, final LocalDateTime tidpunkt, final String diarieNr,
		final String mall, final String worddokument, final String bifogadfil) {
		return event(handelseId, fsId, arendeId, handelseTyp, tidpunkt, diarieNr, mall, worddokument, bifogadfil, null);
	}

	/**
	 * @return an event that is not marked as confidential
	 */
	public static EventRow event(final Integer handelseId, final Integer fsId, final Integer arendeId, final String handelseTyp, final LocalDateTime tidpunkt, final String diarieNr,
		final String mall, final String worddokument, final String bifogadfil, final String text) {
		return new EventRow(handelseId, fsId, arendeId, handelseTyp, tidpunkt, diarieNr, mall, worddokument, bifogadfil, text, false);
	}
}
