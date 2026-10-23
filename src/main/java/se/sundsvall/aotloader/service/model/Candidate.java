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
 * @param errand        what the errand says
 * @param asset         the post in PartyAssets, null for an errand without one
 * @param files         the files that follow the errand
 * @param investigation the investigation of an inspection errand, null for an errand without one
 * @param permitLink    for an inspection errand, what decides the relation to the permit errand, otherwise null
 * @param measures      the measures of an inspection visit, its remarks (decision 57)
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
	ErrandContent errand,
	AssetData asset,
	CandidateFiles files,
	InvestigationData investigation,
	PermitLink permitLink,
	List<MeasureData> measures) {

	public Candidate {
		requireNonNull(key, "key");
		requireNonNull(type, "type");
		ruleHits = ruleHits == null ? List.of() : List.copyOf(ruleHits);
		persons = persons == null ? List.of() : List.copyOf(persons);
		errand = errand == null ? ErrandContent.NONE : errand;
		files = files == null ? CandidateFiles.NONE : files;
		measures = measures == null ? List.of() : List.copyOf(measures);
	}

	/**
	 * @return the candidate with another post in PartyAssets
	 */
	public Candidate withAsset(final AssetData value) {
		return new Candidate(key, type, placeName, placeNumber, ruleHits, holder, place, persons, errand, value, files, investigation, permitLink, measures);
	}

	public static Builder builder(final ItemKey key, final ErrandType type) {
		return new Builder(key, type);
	}

	@Override
	public String toString() {
		return "Candidate[key=" + key + ", type=" + type + "]";
	}

	/**
	 * What the errand of a candidate says.
	 *
	 * @param title        the title of the errand
	 * @param description  the description of the errand
	 * @param labelPaths   the resource paths of the labels the errand gets, besides the label for migrated errands
	 * @param externalTags the external tags of the errand, besides the one that marks it as migrated
	 * @param parameters   the parameters of the errand
	 */
	public record ErrandContent(String title, String description, List<String> labelPaths, Map<String, String> externalTags, List<Parameter> parameters) {

		/** An errand that says nothing. */
		public static final ErrandContent NONE = new ErrandContent(null, null, List.of(), Map.of(), List.of());

		public ErrandContent {
			labelPaths = labelPaths == null ? List.of() : List.copyOf(labelPaths);
			externalTags = externalTags == null ? Map.of() : unmodifiableMap(new LinkedHashMap<>(externalTags));
			parameters = parameters == null ? List.of() : List.copyOf(parameters);
		}

		@Override
		public String toString() {
			return "ErrandContent[labelPaths=" + labelPaths + "]";
		}
	}

	/**
	 * The files of a candidate.
	 *
	 * @param references the files that follow the errand
	 * @param onPost     the files that also go on the post in PartyAssets, each one of the files of the errand
	 * @param details    the purpose and date of the errand's files that have either, by file
	 */
	public record CandidateFiles(List<FileReference> references, List<AssetFile> onPost, Map<FileReference, FileDetails> details) {

		/** No files. */
		public static final CandidateFiles NONE = new CandidateFiles(List.of(), List.of(), Map.of());

		public CandidateFiles {
			references = references == null ? List.of() : List.copyOf(references);
			onPost = onPost == null ? List.of() : List.copyOf(onPost);
			details = details == null ? Map.of() : Map.copyOf(details);
		}

		@Override
		public String toString() {
			return "CandidateFiles[references=" + references.size() + ", onPost=" + onPost.size() + "]";
		}

		/**
		 * @return the purpose and date of the file, {@link FileDetails#NONE} when it has neither
		 */
		public FileDetails detailsOf(final FileReference file) {
			return details.getOrDefault(file, FileDetails.NONE);
		}
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
		private final List<MeasureData> measures = new ArrayList<>();
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

		/**
		 * Adds the files of the item, those of them that also go on the post in PartyAssets, and their purposes and dates.
		 */
		public Builder files(final List<FileReference> references, final List<AssetFile> onPost, final Map<FileReference, FileDetails> details) {
			return files(references).assetFiles(onPost).fileDetails(details);
		}

		public Builder investigation(final InvestigationData value) {
			this.investigation = value;
			return this;
		}

		public Builder permitLink(final PermitLink value) {
			this.permitLink = value;
			return this;
		}

		public Builder measures(final List<MeasureData> values) {
			this.measures.addAll(values);
			return this;
		}

		public Candidate build() {
			return new Candidate(key, type, placeName, placeNumber, ruleHits, holder, place, persons, new ErrandContent(title, description, labelPaths, externalTags, parameters), asset,
				new CandidateFiles(files, assetFiles, fileDetails), investigation, permitLink, measures);
		}
	}
}
