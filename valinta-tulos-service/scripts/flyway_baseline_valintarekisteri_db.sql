CREATE TABLE schema_version (
    version_rank integer NOT NULL,
    installed_rank integer NOT NULL,
    version character varying(50) NOT NULL,
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);
ALTER TABLE schema_version OWNER TO oph;
ALTER TABLE ONLY schema_version
    ADD CONSTRAINT schema_version_pk PRIMARY KEY (version);
CREATE INDEX schema_version_ir_idx ON schema_version USING btree (installed_rank);
CREATE INDEX schema_version_s_idx ON schema_version USING btree (success);
CREATE INDEX schema_version_vr_idx ON schema_version USING btree (version_rank);

INSERT INTO schema_version (version_rank, installed_rank, version,
                            description, type, script, checksum, installed_by,
                            execution_time, success)
    VALUES (1, 1, '1', '<< Manual Flyway Baseline >>', 'BASELINE',
            '<< Flyway Baseline >>', NULL, '', 0, true);
