package se.sundsvall.aotloader.integration.db.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.Source;

import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * A row of the report of a run: what the run did with one source item.
 */
@Entity
@Table(name = "run_item")
public class RunItemEntity {

	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "run_id", nullable = false, updatable = false)
	private String runId;

	@Enumerated(EnumType.STRING)
	@Column(name = "source", nullable = false, updatable = false)
	private Source source;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, updatable = false)
	private Kind kind;

	@Column(name = "source_id", nullable = false, updatable = false)
	private String sourceId;

	@Enumerated(EnumType.STRING)
	@Column(name = "outcome", nullable = false)
	private Outcome outcome;

	@Column(name = "rules")
	private String rules;

	@Column(name = "detail")
	private String detail;

	@Column(name = "place_name")
	private String placeName;

	@Column(name = "place_number")
	private String placeNumber;

	@Column(name = "errand_number")
	private String errandNumber;

	@Column(name = "created", nullable = false, updatable = false)
	private OffsetDateTime created;

	public Long getId() {
		return id;
	}

	public void setId(final Long id) {
		this.id = id;
	}

	public String getRunId() {
		return runId;
	}

	public void setRunId(final String runId) {
		this.runId = runId;
	}

	public Source getSource() {
		return source;
	}

	public void setSource(final Source source) {
		this.source = source;
	}

	public Kind getKind() {
		return kind;
	}

	public void setKind(final Kind kind) {
		this.kind = kind;
	}

	public String getSourceId() {
		return sourceId;
	}

	public void setSourceId(final String sourceId) {
		this.sourceId = sourceId;
	}

	public Outcome getOutcome() {
		return outcome;
	}

	public void setOutcome(final Outcome outcome) {
		this.outcome = outcome;
	}

	public String getRules() {
		return rules;
	}

	public void setRules(final String rules) {
		this.rules = rules;
	}

	public String getDetail() {
		return detail;
	}

	public void setDetail(final String detail) {
		this.detail = detail;
	}

	public String getPlaceName() {
		return placeName;
	}

	public void setPlaceName(final String placeName) {
		this.placeName = placeName;
	}

	public String getPlaceNumber() {
		return placeNumber;
	}

	public void setPlaceNumber(final String placeNumber) {
		this.placeNumber = placeNumber;
	}

	public String getErrandNumber() {
		return errandNumber;
	}

	public void setErrandNumber(final String errandNumber) {
		this.errandNumber = errandNumber;
	}

	public OffsetDateTime getCreated() {
		return created;
	}

	public void setCreated(final OffsetDateTime created) {
		this.created = created;
	}

	@Override
	public boolean equals(final Object o) {
		return this == o || (o instanceof final RunItemEntity other && Objects.equals(id, other.id));
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public String toString() {
		return "RunItemEntity [id=" + id + ", runId=" + runId + ", source=" + source + ", kind=" + kind + ", sourceId=" + sourceId + ", outcome=" + outcome + ", rules=" + rules + ", detail=" + detail + ", placeName=" + placeName + ", placeNumber="
			+ placeNumber + ", errandNumber=" + errandNumber + ", created=" + created + "]";
	}
}
