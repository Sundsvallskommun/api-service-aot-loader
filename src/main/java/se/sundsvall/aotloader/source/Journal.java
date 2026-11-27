package se.sundsvall.aotloader.source;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import se.sundsvall.aotloader.service.model.InvestigationTexts;
import se.sundsvall.aotloader.service.model.InvestigationTexts.Part;
import se.sundsvall.aotloader.support.DiaryNumbers;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

/**
 * Writes the events and decisions of the source as lines of a journal, "ÅÅÅÅ-MM-DD heading: text", in order of time
 * (decisions 44, 46, 47, 52, 53 and 56).
 */
public final class Journal {

	private static final Comparator<Entry> ORDER = Comparator.comparing(Entry::tidpunkt, Comparator.nullsFirst(Comparator.naturalOrder()));
	private static final String CONFIDENTIAL = "sekretess";

	private Journal() {}

	/**
	 * @param  notes     the notes of the errand
	 * @param  events    the events of the errand, also those without a text
	 * @param  decisions the decisions of the errand
	 * @return           the texts of the investigation: the notes, the events and the decisions, under the headings
	 *                   "Noteringar", "Händelser" and "Beslut"
	 */
	public static InvestigationTexts investigation(final Collection<String> notes, final Collection<Entry> events, final Collection<Entry> decisions) {
		return InvestigationTexts.of(new Part("Noteringar", Texts.join("\n", notes)), new Part("Händelser", text(events)), new Part("Beslut", text(decisions)));
	}

	/**
	 * @param  type             the text of the event type
	 * @param  confidential     true when the source marks the event as confidential
	 * @param  otherDiaryNumber the diary number of the event when it is another than that of its errand, null otherwise
	 * @return                  the heading of an event: its type, with "(sekretess)" when confidential (decision 52) and
	 *                          the other diary number, "Remissvar (sekretess, diarienummer IAN-2019-5)" (decision 56)
	 */
	public static String eventHeading(final String type, final boolean confidential, final String otherDiaryNumber) {
		final var marks = Texts.join(", ", Arrays.asList(confidential ? CONFIDENTIAL : null, otherDiaryNumber == null ? null : "diarienummer " + otherDiaryNumber));
		return marks == null ? type : Texts.joinWords(type, "(" + marks + ")");
	}

	/**
	 * @param  event  the diary number of the event
	 * @param  errand the diary number of its errand, null for an event without an errand
	 * @return        the diary number of the event when it is another than that of its errand, which shows that it was
	 *                registered in another case; null when it is the same, compared as {@link DiaryNumbers} does, when it
	 *                is blank or when there is no errand to compare with (decision 56)
	 */
	public static String otherDiaryNumber(final String event, final String errand) {
		final var own = DiaryNumbers.normalize(event);
		final var errands = DiaryNumbers.normalize(errand);
		return own == null || errands == null || own.equals(errands) ? null : Texts.trimToNull(event);
	}

	/**
	 * @param  type  the text of the decision type
	 * @param  maker the name or code of who made the decision, null when unknown
	 * @return       the heading of a decision: its type and who made it, "Varning, beslutsfattare Anna Berg" (decision 53)
	 */
	public static String decisionHeading(final String type, final String maker) {
		return Texts.join(", ", Arrays.asList(type, maker == null ? null : "beslutsfattare " + maker));
	}

	/**
	 * @param  type  the text of the decision type
	 * @param  maker the name or code of who made the decision, null when unknown
	 * @return       the decision as an entry, headed by its type and who made it, as {@link #decisionHeading} gives them
	 */
	public static Entry decisionEntry(final SourceDecision decision, final String type, final String maker) {
		return new Entry(decision.beslutad(), decisionHeading(type, maker), decision.beslutsText());
	}

	/**
	 * @return the entries as lines in order of time, an entry without a time first; null when there are none
	 */
	public static String text(final Collection<Entry> entries) {
		return Texts.join("\n", lines(entries));
	}

	/**
	 * @return the entries as lines in order of time, an entry without a time first, leaving out an entry without anything
	 */
	public static List<String> lines(final Collection<Entry> entries) {
		// A stable sort keeps the order of the source for entries at the same time
		return entries.stream().sorted(ORDER).map(Entry::line).filter(Objects::nonNull).toList();
	}

	/**
	 * An event or decision of the source.
	 *
	 * @param tidpunkt when it was, null when the source has no time
	 * @param heading  what it was, such as the text of its type
	 * @param text     its own text, null when it has none
	 */
	public record Entry(LocalDateTime tidpunkt, String heading, String text) {

		@Override
		public String toString() {
			return "Entry[tidpunkt=" + tidpunkt + ", heading=" + heading + "]";
		}

		/**
		 * @return the entry as "ÅÅÅÅ-MM-DD heading: text", without what it lacks; null for an entry with neither a heading
		 *         nor a text, since a date alone tells nothing
		 */
		String line() {
			final var ownText = Texts.trimToNull(text);
			if (Texts.trimToNull(heading) == null && ownText == null) {
				return null;
			}
			final var date = Optional.ofNullable(tidpunkt).map(LocalDateTime::toLocalDate).map(SourceValues::format).orElse(null);
			final var title = Texts.joinWords(date, heading);
			if (title == null) {
				return ownText;
			}
			return ownText == null ? title : title + ": " + ownText;
		}
	}
}
