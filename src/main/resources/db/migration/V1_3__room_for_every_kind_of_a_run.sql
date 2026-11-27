-- The kinds of a run of every kind, 'TILLSTAND,ANMALAN,TILLSYN,TILLSYNSBESOK,DOKUMENTATION', take more than 50 characters since
-- the documentation errands (decision 71).
ALTER TABLE migration_run ALTER COLUMN kinds SET DATA TYPE VARCHAR(255);
