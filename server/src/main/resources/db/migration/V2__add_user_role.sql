-- ============================================================
-- V2: 用户角色（管理后台鉴权用）
-- ============================================================

ALTER TABLE `users`
  ADD COLUMN `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：USER / ADMIN';

-- 提升管理员：把下面改成你的用户名后执行（或在管理后台/DB 手工执行）
-- UPDATE `users` SET `role` = 'ADMIN' WHERE `username` = 'your_username';
