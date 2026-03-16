-- V28__im_avatar.sql
-- IM 头像配置：DiceBear style + seed
ALTER TABLE im_identity ADD COLUMN avatar_style VARCHAR(64) DEFAULT 'thumbs';
ALTER TABLE im_identity ADD COLUMN avatar_seed  TEXT;

ALTER TABLE im_contacts ADD COLUMN avatar_style VARCHAR(64);
ALTER TABLE im_contacts ADD COLUMN avatar_seed  TEXT;
