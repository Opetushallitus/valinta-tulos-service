/*
DROP SCHEMA public CASCADE;

DROP ROLE IF EXISTS oph;

CREATE ROLE "oph" WITH SUPERUSER;

CREATE SCHEMA public AUTHORIZATION oph;

GRANT ALL ON SCHEMA public TO oph;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO public;

SET default_tablespace = '';
*/


SET client_encoding = 'UTF8';

CREATE TABLE deleted (
    id integer NOT NULL,
    poistaja character varying NOT NULL,
    "timestamp" bigint NOT NULL
);
ALTER TABLE deleted OWNER TO oph;
CREATE SEQUENCE deleted_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
ALTER TABLE deleted_id_seq OWNER TO oph;
ALTER SEQUENCE deleted_id_seq OWNED BY deleted.id;
ALTER TABLE ONLY deleted ALTER COLUMN id SET DEFAULT nextval('deleted_id_seq'::regclass);
SELECT pg_catalog.setval('deleted_id_seq', 1, false);
ALTER TABLE ONLY deleted
    ADD CONSTRAINT deleted_pkey PRIMARY KEY (id);


CREATE TABLE hakufamilies (
    id integer NOT NULL,
    name character varying NOT NULL
);
ALTER TABLE hakufamilies OWNER TO oph;
CREATE SEQUENCE hakufamilies_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
ALTER TABLE hakufamilies_id_seq OWNER TO oph;
ALTER SEQUENCE hakufamilies_id_seq OWNED BY hakufamilies.id;
ALTER TABLE ONLY hakufamilies ALTER COLUMN id SET DEFAULT nextval('hakufamilies_id_seq'::regclass);
SELECT pg_catalog.setval('hakufamilies_id_seq', 1, false);
ALTER TABLE ONLY hakufamilies
    ADD CONSTRAINT hakufamilies_pkey PRIMARY KEY (id);


CREATE TABLE hakukohteet (
    "hakukohdeOid" character varying NOT NULL,
    "hakuOid" character varying NOT NULL,
    "familyId" integer,
    kausi character varying,
    "tutkintoonJohtava" boolean NOT NULL,
    CONSTRAINT kausi_xor_family CHECK (((("familyId" IS NOT NULL) AND (kausi IS NULL)) OR ((kausi IS NOT NULL) AND ("familyId" IS NULL))))
);
ALTER TABLE hakukohteet OWNER TO oph;
ALTER TABLE ONLY hakukohteet
    ADD CONSTRAINT hakukohteet_pkey PRIMARY KEY ("hakukohdeOid");
CREATE UNIQUE INDEX kohde_family_index ON hakukohteet USING btree ("hakukohdeOid", "familyId", "tutkintoonJohtava");

CREATE TABLE haut (
    "hakuOid" character varying NOT NULL,
    "familyId" integer NOT NULL
);
ALTER TABLE haut OWNER TO oph;
ALTER TABLE ONLY haut
    ADD CONSTRAINT haut_pkey PRIMARY KEY ("hakuOid");
CREATE UNIQUE INDEX haku_family_index ON haut USING btree ("hakuOid", "familyId");
ALTER TABLE ONLY haut
    ADD CONSTRAINT family_fk FOREIGN KEY ("familyId") REFERENCES hakufamilies(id);

ALTER TABLE ONLY hakukohteet
    ADD CONSTRAINT haku_fk FOREIGN KEY ("hakuOid", "familyId") REFERENCES haut("hakuOid", "familyId");

CREATE TABLE kaudet (
    kausi character varying NOT NULL,
    ajanjakso tsrange NOT NULL,
    CONSTRAINT kausi_format CHECK (((kausi)::text ~ similar_escape('[0-9]{4}[KS]'::text, NULL::text)))
);
ALTER TABLE kaudet OWNER TO oph;
ALTER TABLE ONLY kaudet
    ADD CONSTRAINT kaudet_pkey PRIMARY KEY (kausi);


CREATE TABLE koulutukset (
    "koulutusOid" character varying NOT NULL,
    alkamiskausi character varying NOT NULL
);
ALTER TABLE koulutukset OWNER TO oph;
ALTER TABLE ONLY koulutukset
    ADD CONSTRAINT koulutukset_pkey PRIMARY KEY ("koulutusOid");
ALTER TABLE ONLY koulutukset
    ADD CONSTRAINT kausi_fk FOREIGN KEY (alkamiskausi) REFERENCES kaudet(kausi);


CREATE TABLE koulutushakukohde (
    "koulutusOid" character varying NOT NULL,
    "hakukohdeOid" character varying NOT NULL
);
ALTER TABLE koulutushakukohde OWNER TO oph;
ALTER TABLE ONLY koulutushakukohde
    ADD CONSTRAINT kh_pk PRIMARY KEY ("koulutusOid", "hakukohdeOid");
ALTER TABLE ONLY koulutushakukohde
    ADD CONSTRAINT kh_hakukohde_fk FOREIGN KEY ("hakukohdeOid") REFERENCES hakukohteet("hakukohdeOid");
ALTER TABLE ONLY koulutushakukohde
    ADD CONSTRAINT kh_koulutus_fk FOREIGN KEY ("koulutusOid") REFERENCES koulutukset("koulutusOid");



CREATE TABLE vastaanotot (
    henkilo character varying NOT NULL,
    hakukohde character varying,
    vanhakohde character varying,
    vanhatarjoaja character varying,
    vanhakausi character varying,
    "familyId" integer,
    active boolean,
    "kkTutkintoonJohtava" boolean,
    ilmoittaja character varying NOT NULL,
    "timestamp" bigint NOT NULL,
    deleted integer,
    CONSTRAINT deleted_must_be_inactive CHECK ((((active IS NULL) AND (deleted IS NOT NULL)) OR (deleted IS NULL))),
    CONSTRAINT hakukohde_or_vanhakohde_defined CHECK ((((hakukohde IS NULL) AND (vanhakohde IS NOT NULL) AND (vanhatarjoaja IS NOT NULL) AND (vanhakausi IS NOT NULL)) OR ((hakukohde IS NOT NULL) AND (vanhakohde IS NULL) AND (vanhatarjoaja IS NULL) AND (vanhakausi IS NULL)))),
    CONSTRAINT use_null_instead_of_false_in_active CHECK (((active = true) OR (active IS NULL)))
);
ALTER TABLE vastaanotot OWNER TO oph;
CREATE UNIQUE INDEX one_active_reception_per_family_index ON vastaanotot USING btree (henkilo, "familyId", active, "kkTutkintoonJohtava");
CREATE INDEX vastaanotot_henkilo_idx ON vastaanotot USING btree (henkilo);
ALTER TABLE ONLY vastaanotot
    ADD CONSTRAINT votto_deletion_fk FOREIGN KEY (deleted) REFERENCES deleted(id);
ALTER TABLE ONLY vastaanotot
    ADD CONSTRAINT votto_fk FOREIGN KEY (hakukohde, "familyId", "kkTutkintoonJohtava") REFERENCES hakukohteet("hakukohdeOid", "familyId", "tutkintoonJohtava");

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM oph;
GRANT ALL ON SCHEMA public TO oph;
GRANT ALL ON SCHEMA public TO PUBLIC;

