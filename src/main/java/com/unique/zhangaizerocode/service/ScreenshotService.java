package com.unique.zhangaizerocode.service;


import com.mybatisflex.core.query.QueryWrapper;
import com.unique.zhangaizerocode.model.dto.user.UserQueryRequest;
import com.unique.zhangaizerocode.model.vo.LoginUserVO;
import com.unique.zhangaizerocode.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;


public interface ScreenshotService  {

/*
 * 通用的截图服务可以得到访问地址
 */
    String generateAndUploadScreenshot(String webUrl);



}


