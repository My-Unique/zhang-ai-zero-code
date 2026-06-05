package com.unique.zhangaizerocode.controller;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.unique.zhangaizerocode.annotation.AuthCheck;
import com.unique.zhangaizerocode.common.BaseResponse;
import com.unique.zhangaizerocode.common.ResultUtils;
import com.unique.zhangaizerocode.constant.UserConstant;
import com.unique.zhangaizerocode.exception.ErrorCode;
import com.unique.zhangaizerocode.exception.ThrowUtils;
import com.unique.zhangaizerocode.model.dto.chathistory.ChatHistoryQueryRequest;
import com.unique.zhangaizerocode.model.entity.User;
import com.unique.zhangaizerocode.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.unique.zhangaizerocode.model.entity.ChatHistory;
import com.unique.zhangaizerocode.service.ChatHistoryService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话历史 控制层。
 *
 * @author <a href="https://github.com/My-Unique">小建</a>
 */
@RestController
@RequestMapping("/chatHistory")
public class ChatHistoryController {

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private UserService userService;
    /**
     * 分页查询某个应用的对话历史（游标查询）
     *
     * @param appId          应用ID
     * @param pageSize       页面大小
     * @param lastCreateTime 最后一条记录的创建时间
     * @param request        请求
     * @return 对话历史分页
     */
    @GetMapping("/app/{appId}")
    public BaseResponse<Page<ChatHistory>> listAppChatHistory(@PathVariable Long appId,
                                                              @RequestParam(defaultValue = "10") int pageSize,
                                                              @RequestParam(required = false) LocalDateTime lastCreateTime,
                                                              HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Page<ChatHistory> result = chatHistoryService.listAppChatHistoryByPage(appId, pageSize, lastCreateTime, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 清空某个应用的全部对话历史
     */
    @DeleteMapping("/app/{appId}")
    public BaseResponse<Boolean> deleteAppChatHistory(@PathVariable Long appId,
                                                      HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        boolean result = chatHistoryService.deleteAppChatHistory(appId, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 管理员分页查询所有对话历史
     *
     * @param chatHistoryQueryRequest 查询请求
     * @return 对话历史分页
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<ChatHistory>> listAllChatHistoryByPageForAdmin(@RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = chatHistoryQueryRequest.getPageNum();
        long pageSize = chatHistoryQueryRequest.getPageSize();
        // 查询数据
        QueryWrapper queryWrapper = chatHistoryService.getQueryWrapper(chatHistoryQueryRequest);
        Page<ChatHistory> result = chatHistoryService.page(Page.of(pageNum, pageSize), queryWrapper);
        return ResultUtils.success(result);
    }


}
