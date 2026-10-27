package se.sundsvall.aotloader.integration.db.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import se.sundsvall.aotloader.service.model.ExceptionOutcome;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Source;

import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * A row of the exception list: what the business decided for an item that cannot be corrected in the source (section
 * 7.1.1).
 */
@Entity
@Table(name = "review_exception")
public class ReviewExceptionEntity {

	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "id")
	private Long id;

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
	private ExceptionOutcome outcome;

	@Column(name = "decided_by", nullable = false)
	private String decidedBy;

	@Column(name = "decided_at", nullable = false)
	private LocalDate decidedAt;

	@Column(name = "comment")
	private String comment;

	@Column(name = "created", nullable = false)
	private OffsetDateTime created;

	public Long getId() {
		return id;
	}

	public void setId(final Long id) {
		this.id = id;
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

	public ExceptionOutcome getOutcome() {
		return outcome;
	}

	public void setOutcome(final ExceptionOutcome outcome) {
		this.outcome = outcome;
	}

	public String getDecidedBy() {
		return decidedBy;
	}

	public void setDecidedBy(final String decidedBy) {
		this.decidedBy = decidedBy;
	}

	public LocalDate getDecidedAt() {
		return decidedAt;
	}

	public void setDecidedAt(final LocalDate decidedAt) {
		this.decidedAt = decidedAt;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(final String comment) {
		this.comment = comment;
	}

	public OffsetDateTime getCreated() {
		return created;
	}

	public void setCreated(final OffsetDateTime created) {
		this.created = created;
	}

	@Override
	public boolean equals(final Object o) {
		return this == o || (o instanceof final ReviewExceptionEntity other && Objects.equals(id, other.id));
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public String toString() {
		return "ReviewExceptionEntity [id=" + id + ", source=" + source + ", kind=" + kind + ", sourceId=" + sourceId + ", outcome=" + outcome + ", decidedBy=" + decidedBy + ", decidedAt=" + decidedAt + ", comment=" + comment + ", created="
			+ created + "]";
	}
}
