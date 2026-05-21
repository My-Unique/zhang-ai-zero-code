package com.unique.zhangaizerocode.service;

import com.mybatisflex.core.service.IService;
import com.unique.zhangaizerocode.model.entity.AppVersion;

import java.util.List;

/**
 *  服务层。
 *
 * @author <a href="https://github.com/My-Unique">小建</a>
 */
public interface AppVersionService extends IService<AppVersion> {


    Long getMaxVersionNo(Long appId);

    List<AppVersion> listByAppId(Long appId);

    AppVersion getByAppIdAndVersionNo(Long appId, Long versionNo);
}
