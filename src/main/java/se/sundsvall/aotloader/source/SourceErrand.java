package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.util.List;

/**
 * A row of Ärende, which AlkT and AlkOL keep with the same columns.
 */
public interface SourceErrand {

	Integer arendeId();

	/**
	 * @return the code of the errand type
	 */
	String arendeTyp();

	String diarieNr();

	/**
	 * @return the day the errand was opened, null when unknown
	 */
	LocalDate oppnad();

	/**
	 * @return the day the errand was closed, null when it is open
	 */
	LocalDate avslutad();

	Integer sokandeId();

	/**
	 * @return the handlers of the errand, HandlaggarID and HandlaggarID2, those that are set
	 */
	List<String> handlaggare();

	/**
	 * @return the day the errand was registered, UpplagdDatum
	 */
	LocalDate upplagd();

	/**
	 * @return the day the errand was opened, or else registered; null when it has neither
	 */
	default LocalDate openedOrRegistered() {
		return oppnad() == null ? upplagd() : oppnad();
	}
}
