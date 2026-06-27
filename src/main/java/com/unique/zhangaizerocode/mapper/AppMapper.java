package com.unique.zhangaizerocode.mapper;

import com.mybatisflex.core.BaseMapper;
import com.unique.zhangaizerocode.model.entity.App;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;


/**
 * 应用 映射层。
 *
 * @author <a href="https://github.com/My-Unique">小建</a>
 */
public interface AppMapper extends BaseMapper<App> {

    /**
     * 原子增加应用下载次数，避免并发下载时丢失统计。
     *
     * @param appId 应用 id
     * @return 影响行数
     */
    @Update("UPDATE app SET downloadCount = COALESCE(downloadCount, 0) + 1 WHERE id = #{appId} AND isDelete = 0")
    int incrementDownloadCount(@Param("appId") Long appId);
}
