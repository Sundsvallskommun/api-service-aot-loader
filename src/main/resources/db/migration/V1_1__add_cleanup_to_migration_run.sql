-- A run is a migration or a cleanup, which removes what the migration wrote to the target systems.
ALTER TABLE migration_run ADD COLUMN cleanup BOOLEAN DEFAULT FALSE NOT NULL;
