package com.unique.zhangaizerocode.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.unique.zhangaizerocode.model.dto.app.AppQueryRequest;
import com.unique.zhangaizerocode.model.dto.app.AppRollbackVersionRequest;
import com.unique.zhangaizerocode.model.dto.app.AppVersionDiffRequest;
import com.unique.zhangaizerocode.model.dto.app.AppVersionFileRequest;
import com.unique.zhangaizerocode.model.entity.App;
import com.unique.zhangaizerocode.model.entity.User;
import com.unique.zhangaizerocode.model.vo.AppVO;
import com.unique.zhangaizerocode.model.vo.AppVersionDiffVO;
import com.unique.zhangaizerocode.model.vo.AppVersionFileVO;
import com.unique.zhangaizerocode.model.vo.AppVersionVO;
import jakarta.servlet.http.HttpServletRequest;
import reactor.core.publisher.Flux;

import java.util.List;


/**
 * 应用 服务层。
 *
 * @author <a href="https://github.com/My-Unique">小建</a>
 */
public interface AppService extends IService<App> {


    AppVO getAppVO(App app);

    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    List<AppVO> getAppVOList(List<App> appList);

    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    String previewApp(Long appId, User loginUser);

    String deployApp(Long appId, User loginUser);

    List<AppVersionVO> listAppVersions(Long appId, HttpServletRequest httpServletRequest);

    Boolean rollbackVersion(AppRollbackVersionRequest request, HttpServletRequest httpServletRequest);

    AppVersionDiffVO diffVersion(AppVersionDiffRequest request, HttpServletRequest httpServletRequest);

    List<String> listVersionFiles(Long appId, Long versionNo, HttpServletRequest httpServletRequest);

    AppVersionFileVO readVersionFile(AppVersionFileRequest request, HttpServletRequest httpServletRequest);

    AppVersionVO saveVersionFile(AppVersionFileRequest request, HttpServletRequest httpServletRequest);

    String previewVersion(Long appId, Long versionNo, HttpServletRequest httpServletRequest);
}
