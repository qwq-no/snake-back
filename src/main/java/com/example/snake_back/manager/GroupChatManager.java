package com.example.snake_back.manager;

import com.example.snake_back.pojo.vo.GroupChatMessageVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
public class GroupChatManager {
    private static final Logger log = LoggerFactory.getLogger(GroupChatManager.class);
    private static final String REDIS_KEY = "group_chat:messages";
    private static final int MAX_MESSAGES = 500;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public GroupChatManager(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void addMessage(GroupChatMessageVO vo) {
        String json = toJson(vo);
        if (json == null) return;

        redis.opsForZSet().add(REDIS_KEY, json, vo.getTimestamp());
        // 超出上限则删除最旧的消息
        Long size = redis.opsForZSet().zCard(REDIS_KEY);
        if (size != null && size > MAX_MESSAGES) {
            redis.opsForZSet().removeRange(REDIS_KEY, 0, size - MAX_MESSAGES - 1);
        }
    }

    public List<GroupChatMessageVO> getAllMessages() {
        Set<String> members = redis.opsForZSet().range(REDIS_KEY, 0, -1);
        return deserialize(members);
    }

    /** 返回 timestamp >= since 的消息 */
    public List<GroupChatMessageVO> getMessagesSince(long since) {
        Set<String> members = redis.opsForZSet().rangeByScore(REDIS_KEY, since, Double.POSITIVE_INFINITY);
        return deserialize(members);
    }

    /** 返回最近 n 条消息 */
    public List<GroupChatMessageVO> getRecentMessages(int n) {
        Set<String> members = redis.opsForZSet().reverseRange(REDIS_KEY, 0, n - 1);
        List<GroupChatMessageVO> result = deserialize(members);
        // reverseRange 返回的是倒序（最新在前），需要反转回正序
        Collections.reverse(result);
        return result;
    }

    /** 删除 timestamp < before 的消息 */
    public int removeMessagesBefore(long before) {
        Long removed = redis.opsForZSet().removeRangeByScore(REDIS_KEY, 0, before);
        return removed != null ? removed.intValue() : 0;
    }

    private String toJson(GroupChatMessageVO vo) {
        try {
            return objectMapper.writeValueAsString(vo);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize GroupChatMessageVO", e);
            return null;
        }
    }

    private List<GroupChatMessageVO> deserialize(Set<String> members) {
        if (members == null || members.isEmpty()) {
            return new ArrayList<>();
        }
        List<GroupChatMessageVO> result = new ArrayList<>(members.size());
        for (String member : members) {
            try {
                result.add(objectMapper.readValue(member, GroupChatMessageVO.class));
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize GroupChatMessageVO", e);
            }
        }
        return result;
    }
}
