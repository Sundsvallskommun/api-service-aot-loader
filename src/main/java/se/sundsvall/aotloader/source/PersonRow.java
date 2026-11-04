package se.sundsvall.aotloader.source;

import java.util.List;

/**
 * An active row of PBI_ägare: a person with significant influence over an owner, which both sources have with the same
 * columns. It is personal data, and {@link #toString()} shows none of it.
 *
 * @param agarId        the owner
 * @param namn          the name of the person
 * @param personnr      the personal identity number, as written
 * @param positionCodes the codes of the person's positions, e.g. VD
 * @param foreign       true for a person without a Swedish personal identity number
 */
public record PersonRow(Integer agarId, String namn, String personnr, List<String> positionCodes, boolean foreign) {

	@Override
	public String toString() {
		return "PersonRow[agarId=" + agarId + "]";
	}
}
