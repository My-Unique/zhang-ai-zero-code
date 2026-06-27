ALTER TABLE app ADD COLUMN currentVersionId BIGINT COMMENT '当前版本 id';
ALTER TABLE app ADD COLUMN versionNo INT DEFAULT 0 COMMENT '当前版本号';
ALTER TABLE app ADD COLUMN deployedVersionNo BIGINT DEFAULT 0 COMMENT '已部署版本号';
ALTER TABLE app ADD COLUMN downloadCount BIGINT DEFAULT 0 NOT NULL COMMENT '下载次数';
ALTER TABLE app ADD COLUMN generationStatus VARCHAR(32) DEFAULT 'not_generated' NOT NULL COMMENT '生成状态：not_generated/generating/succeeded/failed';
ALTER TABLE app ADD COLUMN visibility VARCHAR(32) DEFAULT 'private' NOT NULL COMMENT '可见范围：private/public';

-- 修复历史数据：旧应用可能已经有版本记录，但 app 表中的当前版本、生成状态、部署版本没有回填。
UPDATE app a
JOIN (
    SELECT appId, MAX(versionNo) AS maxVersionNo
    FROM app_version
    WHERE isDelete = 0
    GROUP BY appId
) latest ON latest.appId = a.id
SET a.versionNo = latest.maxVersionNo
WHERE a.isDelete = 0
  AND (a.versionNo IS NULL OR a.versionNo <= 0);

UPDATE app a
JOIN app_version v
  ON v.appId = a.id
 AND v.versionNo = a.versionNo
 AND v.isDelete = 0
SET a.currentVersionId = v.id
WHERE a.isDelete = 0
  AND a.versionNo > 0
  AND (a.currentVersionId IS NULL OR a.currentVersionId <> v.id);

UPDATE app
SET generationStatus = 'succeeded'
WHERE isDelete = 0
  AND versionNo > 0
  AND (generationStatus IS NULL OR generationStatus = '' OR generationStatus = 'not_generated');

UPDATE app
SET generationStatus = 'not_generated'
WHERE isDelete = 0
  AND (versionNo IS NULL OR versionNo <= 0)
  AND (generationStatus IS NULL OR generationStatus = '' OR generationStatus IN ('generating', 'succeeded'));

UPDATE app
SET deployedVersionNo = versionNo
WHERE isDelete = 0
  AND deployKey IS NOT NULL
  AND deployKey <> ''
  AND versionNo > 0
  AND (deployedVersionNo IS NULL OR deployedVersionNo <= 0);

UPDATE app
SET deployKey = NULL,
    deployedTime = NULL,
    deployedVersionNo = 0
WHERE isDelete = 0
  AND (versionNo IS NULL OR versionNo <= 0)
  AND deployKey IS NOT NULL
  AND deployKey <> '';
ALTER TABLE app ADD COLUMN downloadCount BIGINT DEFAULT 0 NOT NULL COMMENT '下载次数';
