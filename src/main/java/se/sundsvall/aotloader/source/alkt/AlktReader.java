package se.sundsvall.aotloader.source.alkt;

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

import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYN;

@Component
public class AlktReader implements SourceReader {

	private final AlktRepository repository;
	private final AotLoaderProperties properties;
	private final Clock clock;

	AlktReader(final AlktRepository repository, final AotLoaderProperties properties, final Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	public Source source() {
		return Source.ALKT;
	}

	@Override
	public Set<Kind> kinds() {
		return Set.of(TILLSTAND, TILLSYN);
	}

	@Override
	public ReaderResult read(final Set<Kind> kinds) {
		final var factory = new AlktCandidateFactory(repository.readSnapshot(), LocalDate.now(clock), properties);
		final var results = new ArrayList<ReaderResult>();
		if (kinds.contains(TILLSTAND)) {
			results.add(factory.permits());
		}
		if (kinds.contains(TILLSYN)) {
			results.add(factory.inspections());
		}
		return ReaderResults.combine(results);
	}
}
