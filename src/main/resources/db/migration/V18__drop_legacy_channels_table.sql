-- Legacy channels module has been replaced by delivery tool configuration.
-- Migrate existing scheduled_tasks channel references from channel id -> channel name when possible.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'channels') THEN
        UPDATE scheduled_tasks st
        SET channel_id = c.name
        FROM channels c
        WHERE st.channel_id = c.id
          AND st.channel_type = c.type
          AND char_length(c.name) <= 36;
    END IF;
END $$;

DROP TABLE IF EXISTS channels;
