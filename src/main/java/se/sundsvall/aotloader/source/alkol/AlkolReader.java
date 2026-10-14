package se.sundsvall.aotloader.source.alkol;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Set;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.ReaderResult;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.source.ReaderResults;
import se.sundsvall.aotloader.source.SourceReader;

import static se.sundsvall.aotloader.service.model.Kind.ANMALAN;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYN;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYNSBESOK;

@Component
public class AlkolReader implements SourceReader {

	private final AlkolRepository repository;
	private final AotLoaderProperties properties;
	private final Clock clock;

	AlkolReader(final AlkolRepository repository, final AotLoaderProperties properties, final Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	public Source source() {
		return Source.ALKOL;
	}

	@Override
	public Set<Kind> kinds() {
		return Set.of(TILLSTAND, ANMALAN, TILLSYN, TILLSYNSBESOK);
	}

	@Override
	public ReaderResult read(final Set<Kind> kinds) {
		final var factory = new AlkolCandidateFactory(repository.readSnapshot(), LocalDate.now(clock), properties);
		final var results = new ArrayList<ReaderResult>();
		if (kinds.contains(TILLSTAND)) {
			results.add(factory.permits());
		}
		if (kinds.contains(ANMALAN)) {
			results.add(factory.notifications());
		}
		if (kinds.contains(TILLSYN)) {
			results.add(factory.inspections());
		}
		if (kinds.contains(TILLSYNSBESOK)) {
			results.add(factory.visits());
		}
		return ReaderResults.combine(results);
	}
}
