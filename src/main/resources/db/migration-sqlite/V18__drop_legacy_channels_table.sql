-- Legacy channels module has been replaced by delivery tool configuration.
-- Migrate existing scheduled_tasks channel references from channel id -> channel name when possible.
UPDATE scheduled_tasks
SET channel_id = (
    SELECT c.name
    FROM channels c
    WHERE c.id = scheduled_tasks.channel_id
      AND c.type = scheduled_tasks.channel_type
    LIMIT 1
)
WHERE EXISTS (
    SELECT 1
    FROM channels c
    WHERE c.id = scheduled_tasks.channel_id
      AND c.type = scheduled_tasks.channel_type
);

DROP TABLE IF EXISTS channels;
