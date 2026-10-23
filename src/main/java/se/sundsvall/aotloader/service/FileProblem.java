package se.sundsvall.aotloader.service;

import java.util.Optional;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.source.files.FileArea;
import se.sundsvall.aotloader.source.files.FileArea.LookupResult;

/**
 * Why a file was not uploaded, with the text of the report for one file and for several.
 */
enum FileProblem {

	MISSING("fil saknas på filarean", "filer saknas på filarean"),
	TOO_LARGE("fil är större än %s och laddas inte upp", "filer är större än %s och laddas inte upp"),
	LINK("länk hoppas över", "länkar hoppas över"),
	INVALID_NAME("filreferens har ett ogiltigt namn", "filreferenser har ett ogiltigt namn"),
	UNREADABLE("fil gick inte att läsa", "filer gick inte att läsa"),
	DIRECTORY_UNAVAILABLE("fil kunde inte kontrolleras, filarean gick inte att läsa", "filer kunde inte kontrolleras, filarean gick inte att läsa"),
	REJECTED("fil togs inte emot av SupportManagement", "filer togs inte emot av SupportManagement"),
	REFUSED_BY_PARTY_ASSETS("fil togs inte emot av PartyAssets och ligger bara på ärendet", "filer togs inte emot av PartyAssets och ligger bara på ärendet"),
	SHARED_NAME("fil har ett namn som också används på ett annat ställe och laddas inte upp", "filer har namn som också används på andra ställen och laddas inte upp");

	private final String one;
	private final String several;

	FileProblem(final String one, final String several) {
		this.one = one;
		this.several = several;
	}

	static FileProblem of(final LookupResult result) {
		return switch (result) {
			case MISSING -> MISSING;
			case TOO_LARGE -> TOO_LARGE;
			case LINK -> LINK;
			case INVALID_NAME -> INVALID_NAME;
			case UNREADABLE -> UNREADABLE;
			case DIRECTORY_UNAVAILABLE -> DIRECTORY_UNAVAILABLE;
			case FOUND -> throw new IllegalArgumentException("A file that is found is not a problem");
		};
	}

	/**
	 * Why a file is not uploaded. A link or an invalid name is that whatever its name; any other file is not uploaded when
	 * another place refers to a file with the same name, since the file may belong to that place.
	 *
	 * @return the problem, or empty when the file is found and is to be uploaded
	 */
	static Optional<FileProblem> of(final FileReference reference, final FileArea.Lookup lookup) {
		return switch (lookup.result()) {
			case LINK, INVALID_NAME, DIRECTORY_UNAVAILABLE -> Optional.of(of(lookup.result()));
			case FOUND -> reference.sharedWithAnotherPlace() ? Optional.of(SHARED_NAME) : Optional.empty();
			default -> Optional.of(reference.sharedWithAnotherPlace() ? SHARED_NAME : of(lookup.result()));
		};
	}

	/**
	 * @return false for a link, which is skipped on purpose (section 6) and is no hit on G14
	 */
	boolean isRuleHit() {
		return this != LINK;
	}

	String text(final int count, final String maxFileSize) {
		return (count == 1 ? one : several).formatted(maxFileSize);
	}
}
