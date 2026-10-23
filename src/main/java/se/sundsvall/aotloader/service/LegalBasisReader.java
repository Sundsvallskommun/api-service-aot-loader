package se.sundsvall.aotloader.service;

import java.io.IOException;
import java.util.Optional;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.source.files.FileArea;
import se.sundsvall.aotloader.source.files.FileArea.LookupResult;
import se.sundsvall.aotloader.source.files.LegalBasis;
import se.sundsvall.aotloader.support.MigrationException;

import static se.sundsvall.aotloader.Constants.ASSET_PARAMETER_LEGAL_BASIS;

/**
 * Reads the legal basis of a permit from its documents in the file area, for its post in PartyAssets (decision 58).
 */
@Component
class LegalBasisReader {

	private final AotLoaderProperties properties;

	LegalBasisReader(final AotLoaderProperties properties) {
		this.properties = properties;
	}

	/**
	 * Reads the legal basis of the permit from the first of its documents that states one, its decision or its certificate
	 * (decision 58), and tells in the report whether one was found. A document whose name another place refers to too is
	 * not read, since it may be that place's, and one that is missing or too large is passed over. A dry run tells in the
	 * report when a document cannot be read.
	 *
	 * @return                    the candidate with the legal basis on its post in PartyAssets; the candidate as it is when
	 *                            no document states one, or when it has no such documents
	 * @throws MigrationException when a document before the one that states the legal basis, or the file area, cannot be
	 *                            read, so that the post is not written without it and the step is made again in the next
	 *                            run
	 */
	Candidate withLegalBasis(final Candidate candidate, final RunContext context, final ReportNotes notes) {
		final var asset = candidate.asset();
		if (asset == null || asset.legalBasisDocuments().isEmpty()) {
			return candidate;
		}
		final var fileArea = context.fileArea(candidate.key().source());
		final Optional<String> legalBasis;
		try {
			legalBasis = asset.legalBasisDocuments().stream()
				.filter(document -> !document.sharedWithAnotherPlace())
				.map(document -> legalBasisOf(fileArea.find(document), candidate))
				.flatMap(Optional::stream)
				.findFirst();
		} catch (final MigrationException e) {
			if (!context.dryRun()) {
				throw e;
			}
			notes.line("Lagrum: tillståndets beslut eller bevis gick inte att läsa");
			return candidate;
		}
		notes.line(legalBasis.map(value -> "Lagrum: %d ur tillståndets beslut eller bevis".formatted(value.split("; ").length))
			.orElse("Lagrum: står inte i tillståndets beslut eller bevis, så tillståndet får inget"));
		return legalBasis.map(value -> candidate.withAsset(asset.withParameter(ASSET_PARAMETER_LEGAL_BASIS, value))).orElse(candidate);
	}

	/**
	 * @return                    the legal basis the document states; empty when it states none, or is missing or too
	 *                            large
	 * @throws MigrationException when the document or the file area cannot be read
	 */
	private Optional<String> legalBasisOf(final FileArea.Lookup lookup, final Candidate candidate) {
		if (lookup.result() == LookupResult.UNREADABLE || lookup.result() == LookupResult.DIRECTORY_UNAVAILABLE) {
			throw unreadable(candidate);
		}
		if (lookup.result() != LookupResult.FOUND) {
			return Optional.empty();
		}
		try {
			return FileArea.read(lookup.path(), properties.maxFileSize().toBytes()).flatMap(LegalBasis::of);
		} catch (final IOException _) {
			throw unreadable(candidate);
		}
	}

	private static MigrationException unreadable(final Candidate candidate) {
		return new MigrationException("Tillståndets beslut eller bevis på filarean för %s gick inte att läsa, och lagrummet läses i nästa körning"
			.formatted(candidate.key().source().getDisplayName()));
	}
}
