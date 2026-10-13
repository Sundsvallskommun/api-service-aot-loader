package se.sundsvall.aotloader.integration.supportmanagement;

import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.Label;
import generated.se.sundsvall.supportmanagement.PageErrand;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.support.ByteArrayMultipartFile;
import se.sundsvall.aotloader.support.FilterValues;
import se.sundsvall.aotloader.support.Locations;
import se.sundsvall.aotloader.support.MigrationException;
import se.sundsvall.dept44.problem.ThrowableProblem;

import static java.util.Collections.emptyList;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static se.sundsvall.aotloader.integration.supportmanagement.configuration.SupportManagementConfiguration.FILE_REFUSED_STATUSES;

@Component
public class SupportManagementIntegration {

	static final int PAGE_SIZE = 100;
	static final int MAX_PAGES = 10;

	private final SupportManagementClient client;
	private final String namespace;

	SupportManagementIntegration(final SupportManagementClient client, final AotLoaderProperties properties) {
		this.client = client;
		this.namespace = properties.namespace();
	}

	/**
	 * @return the label tree of the namespace
	 */
	public List<Label> getLabelTree(final String municipalityId) {
		return Optional.ofNullable(client.getLabels(municipalityId, namespace).getLabelStructure()).orElse(emptyList());
	}

	/**
	 * Finds the errands, drafts included, that carry the external tag. The search names lifecycle, since a search that
	 * does not leaves the drafts out, and a rerun after an interruption would then create a second draft.
	 *
	 * @param  municipalityId the municipality
	 * @param  key            the key of the tag
	 * @param  value          the value of the tag
	 * @return                the errands that carry exactly that tag
	 */
	public List<Errand> findErrandsByExternalTag(final String municipalityId, final String key, final String value) {
		if (!FilterValues.isSafe(key) || !FilterValues.isSafe(value)) {
			throw new MigrationException("Den externa taggen innehåller tecken som inte går att söka på");
		}
		final var filter = "externalTags.key:'%s' and externalTags.value:'%s' and (lifecycle:'DRAFT' or lifecycle:'ACTIVE')".formatted(key, value);
		final var errands = new ArrayList<Errand>();
		for (var page = 0; page < MAX_PAGES; page++) {
			final var result = client.findErrands(municipalityId, namespace, filter, page, PAGE_SIZE);
			final var content = Optional.ofNullable(result).map(PageErrand::getContent).orElse(emptyList());
			errands.addAll(content);
			if (content.size() < PAGE_SIZE || Boolean.TRUE.equals(result.getLast())) {
				break;
			}
		}
		// The filter joins the tags, so a key on one tag and a value on another would also match
		return errands.stream()
			.filter(errand -> hasTag(errand, key, value))
			.toList();
	}

	/**
	 * @return the errand, or empty when it does not exist
	 */
	public Optional<Errand> getErrand(final String municipalityId, final String errandId) {
		try {
			return Optional.ofNullable(client.getErrand(municipalityId, namespace, errandId));
		} catch (final ThrowableProblem problem) {
			if (problem.getStatus() == NOT_FOUND) {
				return Optional.empty();
			}
			throw problem;
		}
	}

	/**
	 * @return the id of the created errand
	 */
	public String createErrand(final String municipalityId, final Errand errand) {
		return Locations.idOf(client.createErrand(municipalityId, namespace, errand), "ärendet");
	}

	public Errand updateErrand(final String municipalityId, final String errandId, final Errand errand) {
		return client.updateErrand(municipalityId, namespace, errandId, errand);
	}

	public List<ErrandAttachment> getAttachments(final String municipalityId, final String errandId) {
		return Optional.ofNullable(client.getAttachments(municipalityId, namespace, errandId)).orElse(emptyList());
	}

	/**
	 * Uploads a file to the errand.
	 *
	 * @param  fileName the name the file gets on the errand
	 * @param  content  the content of the file
	 * @return          true when SupportManagement took the file, false when it refused the file itself
	 */
	public boolean createAttachment(final String municipalityId, final String errandId, final String fileName, final byte[] content) {
		try {
			client.createAttachment(municipalityId, namespace, errandId, new ByteArrayMultipartFile(SupportManagementClient.ATTACHMENT_PART, fileName, content));
			return true;
		} catch (final ThrowableProblem problem) {
			if (problem.getStatus() != null && FILE_REFUSED_STATUSES.contains(problem.getStatus().value())) {
				return false;
			}
			throw problem;
		}
	}

	private static boolean hasTag(final Errand errand, final String key, final String value) {
		return Optional.ofNullable(errand.getExternalTags()).orElse(Set.of()).stream()
			.anyMatch(tag -> key.equals(tag.getKey()) && value.equals(tag.getValue()));
	}
}
