-- database: presto; groups: varchar,quarantine; tables: orc_char_dictionary;
--!
SELECT c_char FROM orc_char_dictionary WHERE c_char IS NOT NULL LIMIT 1
--!
-- delimiter: |; trimValues: true; ignoreOrder: true;
column_val
