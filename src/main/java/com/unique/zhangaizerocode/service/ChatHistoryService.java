package com.unique.zhangaizerocode.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.unique.zhangaizerocode.model.dto.chathistory.ChatHistoryQueryRequest;
import com.unique.zhangaizerocode.model.entity.ChatHistory;
import com.unique.zhangaizerocode.model.entity.User;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author <a href="https://github.com/My-Unique">小建</a>
 */
public interface ChatHistoryService extends IService<ChatHistory> {
    boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    boolean deleteByAppId(Long appId);

    boolean deleteAppChatHistory(Long appId, User loginUser);

    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);


    int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);
}
