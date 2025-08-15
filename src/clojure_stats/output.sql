
-- src/clojure_stats/output.sql


-- :name create-form-type :!

CREATE TYPE form_type AS ENUM ('symbol', 'list', 'var', 'coll', 'data', 'other');


-- :name create-forms-table :!
CREATE SEQUENCE form_id_serial;
-- ;;
CREATE TABLE forms (form_id INTEGER PRIMARY KEY DEFAULT nextval('form_id_serial'),
	form_type form_type,
	form VARCHAR,
	resolved_symbol VARCHAR,
	meta VARCHAR);


-- :name insert-forms :!

INSERT INTO forms (form_type, form, resolved_symbol, meta)
VALUES (:type, :form, :resolved-symbol, :meta);

