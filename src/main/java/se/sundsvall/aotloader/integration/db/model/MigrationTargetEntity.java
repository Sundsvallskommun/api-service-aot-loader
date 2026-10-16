package se.sundsvall.aotloader.integration.db.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * The target environment the local database belongs to: the one row is written by the first run.
 */
@Entity
@Table(name = "migration_target")
public class MigrationTargetEntity {

	public static final int SINGLE_ROW_ID = 1;

	@Id
	@Column(name = "id")
	private Integer id;

	@Column(name = "support_management_url", nullable = false)
	private String supportManagementUrl;

	@Column(name = "created", nullable = false)
	private OffsetDateTime created;

	public Integer getId() {
		return id;
	}

	public void setId(final Integer id) {
		this.id = id;
	}

	public String getSupportManagementUrl() {
		return supportManagementUrl;
	}

	public void setSupportManagementUrl(final String supportManagementUrl) {
		this.supportManagementUrl = supportManagementUrl;
	}

	public OffsetDateTime getCreated() {
		return created;
	}

	public void setCreated(final OffsetDateTime created) {
		this.created = created;
	}

	@Override
	public boolean equals(final Object o) {
		return this == o || (o instanceof final MigrationTargetEntity other && Objects.equals(id, other.id));
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public String toString() {
		return "MigrationTargetEntity [id=" + id + ", supportManagementUrl=" + supportManagementUrl + ", created=" + created + "]";
	}
}
