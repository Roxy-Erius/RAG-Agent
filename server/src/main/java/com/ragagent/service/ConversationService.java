package com.ragagent.service;

import com.ragagent.model.Conversation;
import com.ragagent.model.Message;
import com.ragagent.repository.ConversationRepository;
import com.ragagent.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;

    public ConversationService(ConversationRepository conversationRepo, MessageRepository messageRepo) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
    }

    /**
     * 获取或创建会话。如果 conversationId 对应的会话不存在，则创建新会话。
     */
    public Conversation getOrCreate(Long userId, String conversationId, String firstMessage) {
        if (conversationId != null) {
            Conversation existing = conversationRepo.findByConversationId(conversationId);
            if (existing != null && existing.getUserId().equals(userId)) {
                return existing;
            }
        }
        // 创建新会话，取首条消息前30字作为标题
        String title = firstMessage != null && firstMessage.length() > 30
                ? firstMessage.substring(0, 30)
                : firstMessage;
        String cid = conversationId != null ? conversationId : java.util.UUID.randomUUID().toString();
        Conversation conv = conversationRepo.create(userId, cid, title);
        log.info("创建新会话 | userId={} | conversationId={} | title=\"{}\"", userId, cid, title);
        return conv;
    }

    /**
     * 保存一条消息到指定会话
     */
    public void saveMessage(Long conversationDbId, String role, String content, List<String> productIds) {
        String productIdsJson = null;
        if (productIds != null && !productIds.isEmpty()) {
            productIdsJson = "[" + String.join(",", productIds.stream()
                    .map(id -> "\"" + id + "\"").toList()) + "]";
        }
        messageRepo.save(conversationDbId, role, content, productIdsJson);
        conversationRepo.updateTimestamp(conversationDbId);
        log.debug("保存消息 | convDbId={} | role={} | len={}", conversationDbId, role,
                content != null ? content.length() : 0);
    }

    /**
     * 获取用户的所有会话列表
     */
    public List<Conversation> listConversations(Long userId) {
        return conversationRepo.findByUserId(userId);
    }

    /**
     * 获取某个会话的所有消息
     */
    public List<Message> getMessages(String conversationId, Long userId) {
        Conversation conv = conversationRepo.findByConversationId(conversationId);
        if (conv == null || !conv.getUserId().equals(userId)) {
            log.warn("无权访问会话 | conversationId={} | userId={}", conversationId, userId);
            return List.of();
        }
        return messageRepo.findByConversationId(conv.getId());
    }

    /**
     * 删除会话
     */
    public boolean deleteConversation(String conversationId, Long userId) {
        boolean ok = conversationRepo.delete(conversationId, userId);
        if (ok) {
            log.info("删除会话 | conversationId={} | userId={}", conversationId, userId);
        }
        return ok;
    }
}
