package com.example.snake_back.websocket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.snake_back.common.utils.TokenUtil;
import com.example.snake_back.manager.SessionContextManager;
import com.example.snake_back.mapper.PrivateMessageMapper;
import com.example.snake_back.pojo.dto.SessionContextDTO;
import com.example.snake_back.pojo.dto.WsResponse;
import com.example.snake_back.pojo.entity.PrivateMessage;
import com.example.snake_back.pojo.vo.PrivateMessageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class TalkHandler {

    private static final DateTimeFormatter DB_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int CONTACT_LIST_LIMIT = 50;
    private static final int CONTACT_QUERY_CAP = 500; // 防止单用户消息量极大时全表扫描

    private final SessionContextManager sessionContextManager;
    private final PrivateMessageMapper privateMessageMapper;
    private final ObjectMapper objectMapper;

    public TalkHandler(SessionContextManager sessionContextManager, PrivateMessageMapper privateMessageMapper,
                       ObjectMapper objectMapper) {
        this.sessionContextManager = sessionContextManager;
        this.privateMessageMapper = privateMessageMapper;
        this.objectMapper = objectMapper;
    }

    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> data = objectMapper.readValue(message.getPayload(), Map.class);
        String type = (String) data.get("type");
        String sessionId = session.getId();
        SessionContextDTO context = sessionContextManager.getSessionContextMap().get(sessionId);
        if (context == null || context.getUserCode() == null) {
            return;
        }

        switch (type) {
            case "private_chat_send" -> handleSend(context, data);
            case "private_chat_history" -> handleHistory(context, data);
            case "talk_contact_list" -> handleContactList(context);
        }
    }

    private void handleSend(SessionContextDTO context, Map<String, Object> data) {
        Integer fromUserCode = parseUserCode(context.getUserCode());
        Integer toUserCode = parseUserCode(String.valueOf(data.get("toUserCode")));
        String content = data.get("content") != null ? String.valueOf(data.get("content")).trim() : "";

        if (fromUserCode == null || toUserCode == null || content.isEmpty()) {
            return;
        }

        String now = LocalDateTime.now().format(DB_DT);
        PrivateMessage msg = new PrivateMessage();
        msg.setId(TokenUtil.newUuid());
        msg.setFromUserCode(fromUserCode);
        msg.setToUserCode(toUserCode);
        msg.setContent(content);
        msg.setCreatedAt(now);
        privateMessageMapper.insert(msg);

        PrivateMessageVO vo = toVO(msg);

        // 发送给发送者
        sendToUser(context, "private_chat_message", vo);

        // 发送给接收者（如果在线）
        String toUserCodeStr = String.format("%06d", toUserCode);
        String toSessionId = sessionContextManager.getUserCodeToSessionIdMap().get(toUserCodeStr);
        if (toSessionId != null) {
            SessionContextDTO toContext = sessionContextManager.getSessionContextMap().get(toSessionId);
            if (toContext != null && "talk".equals(toContext.getPageType())) {
                sendToUser(toContext, "private_chat_message", vo);
            }
        }
    }

    private void handleHistory(SessionContextDTO context, Map<String, Object> data) {
        Integer myUserCode = parseUserCode(context.getUserCode());
        Integer withUserCode = parseUserCode(String.valueOf(data.get("withUserCode")));

        if (myUserCode == null || withUserCode == null) {
            return;
        }

        int page = Math.max(parseIntParam(data, "page", 1), 1);
        int size = Math.min(parseIntParam(data, "size", DEFAULT_PAGE_SIZE), MAX_PAGE_SIZE);

        // 总数
        long total = privateMessageMapper.selectCount(
                new LambdaQueryWrapper<PrivateMessage>()
                        .and(w -> w
                                .eq(PrivateMessage::getFromUserCode, myUserCode)
                                .eq(PrivateMessage::getToUserCode, withUserCode)
                        )
                        .or(w -> w
                                .eq(PrivateMessage::getFromUserCode, withUserCode)
                                .eq(PrivateMessage::getToUserCode, myUserCode)
                        )
        );

        // 分页查询：倒序取最新的一页，反转发给前端按时间正序
        int offset = (page - 1) * size;
        List<PrivateMessage> messages = privateMessageMapper.selectList(
                new LambdaQueryWrapper<PrivateMessage>()
                        .and(w -> w
                                .eq(PrivateMessage::getFromUserCode, myUserCode)
                                .eq(PrivateMessage::getToUserCode, withUserCode)
                        )
                        .or(w -> w
                                .eq(PrivateMessage::getFromUserCode, withUserCode)
                                .eq(PrivateMessage::getToUserCode, myUserCode)
                        )
                        .orderByDesc(PrivateMessage::getCreatedAt)
                        .last("LIMIT " + offset + "," + size)
        );

        List<PrivateMessageVO> vos = messages.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        Collections.reverse(vos);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("messages", vos);
        responseData.put("total", (int) total);
        responseData.put("page", page);
        responseData.put("size", size);
        responseData.put("hasMore", total > (long) page * size);

        WsResponse<Map<String, Object>> response = new WsResponse<>();
        response.setPageType("talk");
        response.setType("private_chat_history");
        response.setData(responseData);
        sendJsonToSession(context.getSession(), response);
    }

    private void handleContactList(SessionContextDTO context) {
        Integer myUserCode = parseUserCode(context.getUserCode());
        if (myUserCode == null) {
            return;
        }

        // 加 LIMIT 防止消息量极大时全表扫描，500 条足够覆盖最近联系人
        List<PrivateMessage> allMessages = privateMessageMapper.selectList(
                new LambdaQueryWrapper<PrivateMessage>()
                        .and(w -> w
                                .eq(PrivateMessage::getFromUserCode, myUserCode)
                                .or()
                                .eq(PrivateMessage::getToUserCode, myUserCode)
                        )
                        .orderByDesc(PrivateMessage::getCreatedAt)
                        .last("LIMIT " + CONTACT_QUERY_CAP)
        );

        // 去重每个对话对象，保留最新的时间
        Map<Integer, String> contactTimeMap = new LinkedHashMap<>();
        for (PrivateMessage msg : allMessages) {
            Integer other = msg.getFromUserCode().equals(myUserCode)
                    ? msg.getToUserCode() : msg.getFromUserCode();
            if (!contactTimeMap.containsKey(other)) {
                contactTimeMap.put(other, msg.getCreatedAt());
            }
        }

        // 构建联系人列表，最多 50 个
        List<Map<String, Object>> contacts = new ArrayList<>();
        int count = 0;
        for (Map.Entry<Integer, String> entry : contactTimeMap.entrySet()) {
            if (count >= CONTACT_LIST_LIMIT) break;
            Map<String, Object> contact = new HashMap<>();
            contact.put("userCode", entry.getKey());
            contact.put("lastMsgTime", entry.getValue());
            contacts.add(contact);
            count++;
        }

        WsResponse<List<Map<String, Object>>> response = new WsResponse<>();
        response.setPageType("talk");
        response.setType("talk_contact_list");
        response.setData(contacts);
        sendJsonToSession(context.getSession(), response);
    }

    private void sendToUser(SessionContextDTO context, String type, PrivateMessageVO data) {
        WsResponse<PrivateMessageVO> response = new WsResponse<>();
        response.setPageType("talk");
        response.setType(type);
        response.setData(data);
        sendJsonToSession(context.getSession(), response);
    }

    private void sendJsonToSession(WebSocketSession session, Object payload) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (IOException e) {
        }
    }

    private PrivateMessageVO toVO(PrivateMessage msg) {
        PrivateMessageVO vo = new PrivateMessageVO();
        vo.setId(msg.getId());
        vo.setFromUserCode(msg.getFromUserCode());
        vo.setToUserCode(msg.getToUserCode());
        vo.setContent(msg.getContent());
        vo.setCreatedAt(msg.getCreatedAt());
        return vo;
    }

    private Integer parseUserCode(String userCode) {
        if (userCode == null || userCode.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(userCode.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int parseIntParam(Map<String, Object> data, String key, int defaultValue) {
        Object value = data.get(key);
        if (value == null) return defaultValue;
        try {
            if (value instanceof Number n) return n.intValue();
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
