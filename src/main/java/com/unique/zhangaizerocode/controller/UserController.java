package com.unique.zhangaizerocode.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.unique.zhangaizerocode.annotation.AuthCheck;
import com.unique.zhangaizerocode.common.BaseResponse;
import com.unique.zhangaizerocode.common.DeleteRequest;
import com.unique.zhangaizerocode.common.ResultUtils;
import com.unique.zhangaizerocode.constant.UserConstant;
import com.unique.zhangaizerocode.exception.BusinessException;
import com.unique.zhangaizerocode.exception.ErrorCode;
import com.unique.zhangaizerocode.exception.ThrowUtils;
import com.unique.zhangaizerocode.manager.CosManager;
import com.unique.zhangaizerocode.model.dto.user.*;
import com.unique.zhangaizerocode.model.vo.LoginUserVO;
import com.unique.zhangaizerocode.model.vo.UserVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import com.unique.zhangaizerocode.model.entity.User;
import com.unique.zhangaizerocode.service.UserService;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 用户 控制层。
 *
 * @author <a href="https://github.com/My-Unique">小建</a>
 */
@RestController
@RequestMapping("/user")
public class UserController {

    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024L;
    private static final Set<String> AVATAR_SUFFIXES = Set.of("jpg", "jpeg", "png", "webp", "gif");

    @Resource
    private UserService userService;

    @Resource
    private CosManager cosManager;
    /**
     * 用户注册
     *
     * @param userRegisterRequest 用户注册请求
     * @return 注册结果
     */
    @PostMapping("register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        long result = userService.userRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(result);
    }
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(loginUser));
    }

    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    /**
     * 创建用户
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtil.copyProperties(userAddRequest, user);
        // 默认密码 12345678
        final String DEFAULT_PASSWORD = "12345678";
        String encryptPassword = userService.getEncryptPassword(DEFAULT_PASSWORD);
        user.setUserPassword(encryptPassword);
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    /**
     * 根据 id 获取用户（仅管理员）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据 id 获取包装类
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id) {
        BaseResponse<User> response = getUserById(id);
        User user = response.getData();
        return ResultUtils.success(userService.getUserVO(user));
    }

    /**
     * 删除用户
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 更新用户
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest, HttpServletRequest request) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        if (!Objects.equals(loginUser.getId(), userUpdateRequest.getId()) && !isAdmin) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限编辑该用户资料");
        }
        User user = new User();
        user.setId(userUpdateRequest.getId());
        user.setUserName(userUpdateRequest.getUserName());
        user.setUserAvatar(userUpdateRequest.getUserAvatar());
        user.setUserProfile(userUpdateRequest.getUserProfile());
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 上传用户头像。
     */
    @PostMapping(value = "/avatar/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<String> uploadUserAvatar(@RequestPart("file") MultipartFile file,
                                                 @RequestParam(required = false) Long userId,
                                                 HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long targetUserId = userId == null ? loginUser.getId() : userId;
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        if (!Objects.equals(loginUser.getId(), targetUserId) && !isAdmin) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限修改该用户头像");
        }
        ThrowUtils.throwIf(userService.getById(targetUserId) == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "头像文件不能为空");
        ThrowUtils.throwIf(file.getSize() > MAX_AVATAR_SIZE, ErrorCode.PARAMS_ERROR, "头像不能超过 2MB");

        String originalFilename = StrUtil.blankToDefault(file.getOriginalFilename(), "avatar");
        String suffix = StrUtil.blankToDefault(FileUtil.getSuffix(originalFilename), "").toLowerCase();
        String contentType = StrUtil.blankToDefault(file.getContentType(), "application/octet-stream");
        ThrowUtils.throwIf(!contentType.startsWith("image/") || !AVATAR_SUFFIXES.contains(suffix),
                ErrorCode.PARAMS_ERROR, "仅支持 jpg、jpeg、png、webp、gif 图片");

        File tempFile = null;
        try {
            tempFile = File.createTempFile("user-avatar-", "." + suffix);
            file.transferTo(tempFile);
            String url = cosManager.uploadFile(buildAvatarCosKey(originalFilename, suffix), tempFile);
            ThrowUtils.throwIf(StrUtil.isBlank(url), ErrorCode.SYSTEM_ERROR, "头像上传失败");
            return ResultUtils.success(url);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "头像上传失败：" + e.getMessage());
        } finally {
            if (tempFile != null) {
                FileUtil.del(tempFile);
            }
        }
    }

    private String buildAvatarCosKey(String originalFilename, String suffix) {
        String datePath = LocalDate.now().toString().replace("-", "/");
        String safeName = originalFilename.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        if (StrUtil.isBlank(safeName)) {
            safeName = "avatar";
        }
        if (StrUtil.isNotBlank(suffix) && !safeName.toLowerCase().endsWith("." + suffix)) {
            safeName = safeName + "." + suffix;
        }
        return String.format("user-avatars/%s/%s_%s",
                datePath, UUID.randomUUID().toString().replace("-", ""), safeName);
    }

    /**
     * 分页获取用户封装列表（仅管理员）
     *
     * @param userQueryRequest 查询请求参数
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = userQueryRequest.getPageNum();
        long pageSize = userQueryRequest.getPageSize();
        Page<User> userPage = userService.page(Page.of(pageNum, pageSize),
                userService.getQueryWrapper(userQueryRequest));
        // 数据脱敏
        Page<UserVO> userVOPage = new Page<>(pageNum, pageSize, userPage.getTotalRow());
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }











}
