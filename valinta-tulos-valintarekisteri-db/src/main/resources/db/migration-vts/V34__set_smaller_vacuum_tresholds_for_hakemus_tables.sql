-- This migration makes autovacuuming tresholds smaller for jonosijat, valinnantulokset and pistetiedot
-- to prevent futures form timing out when querying. Details on how to revert them are in
-- valinta-tulos-valintarekisteri-db/README.md

alter table jonosijat set (autovacuum_enabled = true, autovacuum_vacuum_scale_factor = 0.0,
  autovacuum_vacuum_threshold = 5000, autovacuum_analyze_scale_factor = 0.0, autovacuum_analyze_threshold = 5000);
alter table valinnantulokset set (autovacuum_enabled = true, autovacuum_vacuum_scale_factor = 0.0,
  autovacuum_vacuum_threshold = 5000, autovacuum_analyze_scale_factor = 0.0, autovacuum_analyze_threshold = 5000);
alter table pistetiedot set (autovacuum_enabled = true, autovacuum_vacuum_scale_factor = 0.0,
  autovacuum_vacuum_threshold = 5000, autovacuum_analyze_scale_factor = 0.0, autovacuum_analyze_threshold = 5000);