package com.unique.zhangaizerocode.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.unique.zhangaizerocode.exception.ErrorCode;
import com.unique.zhangaizerocode.exception.ThrowUtils;
import com.unique.zhangaizerocode.mapper.AppVersionMapper;
import com.unique.zhangaizerocode.model.entity.AppVersion;
import com.unique.zhangaizerocode.service.AppVersionService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 *  服务层实现。
 *
 * @author <a href="https://github.com/My-Unique">小建</a>
 */
@Service
public class AppVersionServiceImpl extends ServiceImpl<AppVersionMapper, AppVersion>  implements AppVersionService{

    @Override
    public Long getMaxVersionNo(Long appId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId)
                .eq("isDelete", 0)
                .orderBy("versionNo", false)
                .limit(1);

        AppVersion latestVersion = this.getOne(queryWrapper);
        return latestVersion == null ? 0L : latestVersion.getVersionNo();
    }

    @Override
    public List<AppVersion> listByAppId(Long appId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId)
                .eq("isDelete", 0)
                .orderBy("versionNo", false);

        return this.list(queryWrapper);
    }

    @Override
    public AppVersion getByAppIdAndVersionNo(Long appId, Long versionNo) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId)
                .eq("versionNo", versionNo)
                .eq("isDelete", 0)
                .limit(1);

        return this.getOne(queryWrapper);
    }

    @Override
    public boolean deleteByAppId(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId);

        return this.remove(queryWrapper);
    }
}
