-- ============================================================================
-- Rezepte im Essensplan: Namen pro Nutzer, Wiedererkennen ueber die Rezept-ID
-- ============================================================================

-- 1) Der Rezeptname war ueber ALLE Nutzer hinweg eindeutig (Spalte "name ... UNIQUE"
--    aus V1). Zwei Nutzer konnten deshalb kein gleichnamiges Rezept haben.
--    Die Einschraenkung wird ueber ihre Spalte gesucht, nicht ueber ihren Namen,
--    damit sie auch dann verschwindet, wenn sie anders heisst als "recipe_name_key".
DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE rel.relname = 'recipe'
          AND nsp.nspname = current_schema()
          AND con.contype = 'u'
          AND array_length(con.conkey, 1) = 1
          AND (SELECT att.attname
               FROM pg_attribute att
               WHERE att.attrelid = rel.oid AND att.attnum = con.conkey[1]) = 'name'
    LOOP
        EXECUTE format('ALTER TABLE recipe DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

-- 2) ID des Rezepts im recipe-Dienst. Bisher wurde ein Rezept nur am Namen
--    wiedererkannt; ein umbenanntes Rezept stand deshalb doppelt da.
--    NULL = im Essensplan frei eingetipptes Gericht oder Altbestand.
ALTER TABLE recipe ADD COLUMN source_recipe_id BIGINT;

-- 3) Im recipe-Dienst geloeschte Rezepte, die noch in Essensplaenen stehen,
--    bleiben fuer diese Plaene erhalten, werden aber nicht mehr angeboten.
ALTER TABLE recipe ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- 4) Eindeutig ist der Name je Nutzer - aber nur unter den nicht geloeschten
--    Rezepten, damit ein geloeschter Name wieder frei ist.
ALTER TABLE recipe DROP CONSTRAINT IF EXISTS unique_user_recipe;
CREATE UNIQUE INDEX recipe_active_name_per_user ON recipe (user_id, name) WHERE NOT deleted;

-- 5) Je Nutzer gibt es zu einem Rezept des recipe-Dienstes hoechstens einen Eintrag.
CREATE UNIQUE INDEX recipe_source_per_user ON recipe (user_id, source_recipe_id) WHERE source_recipe_id IS NOT NULL;
