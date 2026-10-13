package se.sundsvall.aotloader.integration.db.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import se.sundsvall.aotloader.service.model.RunStatus;

/**
 * A run of the migration, a full run or the run of a single item.
 */
@Entity
@Table(name = "migration_run")
public class MigrationRunEntity {

	@Id
	@Column(name = "id")
	private String id;

	@Column(name = "municipality_id", nullable = false)
	private String municipalityId;

	@Column(name = "dry_run", nullable = false)
	private boolean dryRun;

	@Column(name = "sources", nullable = false)
	private String sources;

	@Column(name = "kinds", nullable = false)
	private String kinds;

	@Column(name = "item_key")
	private String itemKey;

	@Column(name = "item_limit")
	private Integer itemLimit;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private RunStatus status;

	@Column(name = "message")
	private String message;

	@Column(name = "started", nullable = false)
	private OffsetDateTime started;

	@Column(name = "finished")
	private OffsetDateTime finished;

	public String getId() {
		return id;
	}

	public void setId(final String id) {
		this.id = id;
	}

	public String getMunicipalityId() {
		return municipalityId;
	}

	public void setMunicipalityId(final String municipalityId) {
		this.municipalityId = municipalityId;
	}

	public boolean isDryRun() {
		return dryRun;
	}

	public void setDryRun(final boolean dryRun) {
		this.dryRun = dryRun;
	}

	public String getSources() {
		return sources;
	}

	public void setSources(final String sources) {
		this.sources = sources;
	}

	public String getKinds() {
		return kinds;
	}

	public void setKinds(final String kinds) {
		this.kinds = kinds;
	}

	public String getItemKey() {
		return itemKey;
	}

	public void setItemKey(final String itemKey) {
		this.itemKey = itemKey;
	}

	public Integer getItemLimit() {
		return itemLimit;
	}

	public void setItemLimit(final Integer itemLimit) {
		this.itemLimit = itemLimit;
	}

	public RunStatus getStatus() {
		return status;
	}

	public void setStatus(final RunStatus status) {
		this.status = status;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(final String message) {
		this.message = message;
	}

	public OffsetDateTime getStarted() {
		return started;
	}

	public void setStarted(final OffsetDateTime started) {
		this.started = started;
	}

	public OffsetDateTime getFinished() {
		return finished;
	}

	public void setFinished(final OffsetDateTime finished) {
		this.finished = finished;
	}

	@Override
	public boolean equals(final Object o) {
		return this == o || (o instanceof final MigrationRunEntity other && Objects.equals(id, other.id));
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public String toString() {
		return "MigrationRunEntity [id=" + id + ", municipalityId=" + municipalityId + ", dryRun=" + dryRun + ", sources=" + sources + ", kinds=" + kinds + ", itemKey=" + itemKey + ", itemLimit=" + itemLimit + ", status=" + status + ", message="
			+ message + ", started=" + started + ", finished=" + finished + "]";
	}
}
