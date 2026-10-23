package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A decision of an errand, which AlkT and AlkOL keep with the same columns.
 */
public interface SourceDecision {

	Integer beslutsId();

	Integer arendeId();

	String beslutsTyp();

	/**
	 * @return when the decision was made, null when unknown
	 */
	LocalDateTime beslutad();

	/**
	 * @return the code of the document template the decision document was made from, null when none
	 */
	String mall();

	String worddokument();

	String bifogadfil();

	/**
	 * @return the text of the decision, null when blank
	 */
	String beslutsText();

	/**
	 * @return the code of who made the decision, BeslutsFattare, null when blank
	 */
	String beslutsfattare();

	/**
	 * @return the date the decision was made, null when unknown
	 */
	default LocalDate beslutsDatum() {
		return beslutad() == null ? null : beslutad().toLocalDate();
	}
}
