package se.sundsvall.aotloader.service.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;

/**
 * A source item as the common flow sees it: the readers of each source turn their rows into candidates, and the rest of
 * the migration is the same whatever the source. A candidate holds personal data in memory only, and
 * {@link #toString()} shows none of it.
 *
 * @param key           the identity of the item
 * @param type          the type of errand the item becomes
 * @param placeName     the name of the serving place or sales place, for the report
 * @param placeNumber   the restaurant number, for the report, null when there is none
 * @param ruleHits      the hits on the review rules that the source itself shows
 * @param holder        the permit holder, null when the source has none
 * @param place         the serving place or sales place, null when the source has none
 * @param persons       the persons with significant influence
 * @param title         the title of the errand
 * @param description   the description of the errand
 * @param labelPaths    the resource paths of the labels the errand gets, besides the label for migrated errands
 * @param externalTags  the external tags of the errand, besides the one that marks it as migrated
 * @param parameters    the parameters of the errand
 * @param asset         the post in PartyAssets, null for an errand without one
 * @param files         the files that follow the errand
 * @param assetFiles    the files that also go on the post in PartyAssets, each one of the files of the errand
 * @param fileDetails   the purpose and date of the errand's files that have either, by file
 * @param investigation the investigation of an inspection errand, null for an errand without one
 * @param permitLink    for an inspection errand, what decides the relation to the permit errand, otherwise null
 */
public record Candidate(
	ItemKey key,
	ErrandType type,
	String placeName,
	String placeNumber,
	List<RuleHit> ruleHits,
	Holder holder,
	Place place,
	List<Person> persons,
	String title,
	String description,
	List<String> labelPaths,
	Map<String, String> externalTags,
	List<Parameter> parameters,
	AssetData asset,
	List<FileReference> files,
	List<AssetFile> assetFiles,
	Map<FileReference, FileDetails> fileDetails,
	InvestigationData investigation,
	PermitLink permitLink) {

	public Candidate {
		requireNonNull(key, "key");
		requireNonNull(type, "type");
		ruleHits = ruleHits == null ? List.of() : List.copyOf(ruleHits);
		persons = persons == null ? List.of() : List.copyOf(persons);
		labelPaths = labelPaths == null ? List.of() : List.copyOf(labelPaths);
		externalTags = externalTags == null ? Map.of() : unmodifiableMap(new LinkedHashMap<>(externalTags));
		parameters = parameters == null ? List.of() : List.copyOf(parameters);
		files = files == null ? List.of() : List.copyOf(files);
		assetFiles = assetFiles == null ? List.of() : List.copyOf(assetFiles);
		fileDetails = fileDetails == null ? Map.of() : Map.copyOf(fileDetails);
	}

	/**
	 * @return the purpose and date of the file, {@link FileDetails#NONE} when it has neither
	 */
	public FileDetails detailsOf(final FileReference file) {
		return fileDetails.getOrDefault(file, FileDetails.NONE);
	}

	public static Builder builder(final ItemKey key, final ErrandType type) {
		return new Builder(key, type);
	}

	@Override
	public String toString() {
		return "Candidate[key=" + key + ", type=" + type + "]";
	}

	/**
	 * Builds a candidate step by step, as the readers find its parts.
	 */
	public static final class Builder {

		private final ItemKey key;
		private final ErrandType type;
		private final List<RuleHit> ruleHits = new ArrayList<>();
		private final List<Person> persons = new ArrayList<>();
		private final List<String> labelPaths = new ArrayList<>();
		private final Map<String, String> externalTags = new LinkedHashMap<>();
		private final List<Parameter> parameters = new ArrayList<>();
		private final List<FileReference> files = new ArrayList<>();
		private final List<AssetFile> assetFiles = new ArrayList<>();
		private final Map<FileReference, FileDetails> fileDetails = new LinkedHashMap<>();
		private String placeName;
		private String placeNumber;
		private Holder holder;
		private Place place;
		private String title;
		private String description;
		private AssetData asset;
		private PermitLink permitLink;
		private InvestigationData investigation;

		private Builder(final ItemKey key, final ErrandType type) {
			this.key = key;
			this.type = type;
		}

		public Builder placeName(final String value) {
			this.placeName = value;
			return this;
		}

		public Builder placeNumber(final String value) {
			this.placeNumber = value;
			return this;
		}

		public Builder ruleHits(final List<RuleHit> values) {
			this.ruleHits.addAll(values);
			return this;
		}

		public Builder holder(final Holder value) {
			this.holder = value;
			return this;
		}

		public Builder place(final Place value) {
			this.place = value;
			return this;
		}

		public Builder persons(final List<Person> values) {
			this.persons.addAll(values);
			return this;
		}

		public Builder title(final String value) {
			this.title = value;
			return this;
		}

		public Builder description(final String value) {
			this.description = value;
			return this;
		}

		/** Adds the label, unless the path is null or blank. */
		public Builder labelPath(final String value) {
			if (value != null && !value.isBlank()) {
				this.labelPaths.add(value);
			}
			return this;
		}

		/** Adds the labels, leaving out the paths that are null or blank. */
		public Builder labelPaths(final Collection<String> values) {
			values.forEach(this::labelPath);
			return this;
		}

		/** Adds the tag, unless the value is null or blank. */
		public Builder externalTag(final String tagKey, final String value) {
			if (value != null && !value.isBlank()) {
				this.externalTags.put(tagKey, value);
			}
			return this;
		}

		public Builder parameters(final List<Parameter> values) {
			this.parameters.addAll(values);
			return this;
		}

		public Builder asset(final AssetData value) {
			this.asset = value;
			return this;
		}

		public Builder files(final List<FileReference> values) {
			this.files.addAll(values);
			return this;
		}

		public Builder assetFiles(final List<AssetFile> values) {
			this.assetFiles.addAll(values);
			return this;
		}

		public Builder fileDetails(final Map<FileReference, FileDetails> values) {
			this.fileDetails.putAll(values);
			return this;
		}

		public Builder investigation(final InvestigationData value) {
			this.investigation = value;
			return this;
		}

		public Builder permitLink(final PermitLink value) {
			this.permitLink = value;
			return this;
		}

		public Candidate build() {
			return new Candidate(key, type, placeName, placeNumber, ruleHits, holder, place, persons, title, description, labelPaths, externalTags, parameters, asset, files, assetFiles, fileDetails, investigation,
				permitLink);
		}
	}
}
