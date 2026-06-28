package com.example.snake_back.websocket;

import com.example.snake_back.common.utils.PageUtil;
import com.example.snake_back.manager.GroupChatManager;
import com.example.snake_back.manager.RoomStateManager;
import com.example.snake_back.pojo.vo.GroupChatMessageVO;
import com.example.snake_back.service.BroadcastService;
import com.example.snake_back.service.SelectService;
import com.example.snake_back.service.SessionContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Component
public class CoreHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final SessionContextService sessionContextService;
    private final OnlineHandler onlineHandler;
    private final HomeHandler homeHandler;
    private final PrepareHandler prepareHandler;
    private final SelectHandler selectHandler;
    private final SingleHandler singleHandler;
    private final TalkHandler talkHandler;
    private final PageUtil pageUtil;
    private final RoomStateManager roomStateManager;
    private final SelectService selectService;
    private final GroupChatManager groupChatManager;
    private final BroadcastService broadcastService;

    public CoreHandler(SessionContextService sessionContextService, ObjectMapper objectMapper,
                       OnlineHandler onlineHandler, HomeHandler homeHandler,
                       PrepareHandler prepareHandler, TalkHandler talkHandler,
                       SelectHandler selectHandler, SingleHandler singleHandler,
                       PageUtil pageUtil, RoomStateManager roomStateManager,
                       SelectService selectService, GroupChatManager groupChatManager,
                       BroadcastService broadcastService) {
        this.objectMapper = objectMapper;

        this.sessionContextService = sessionContextService;
        this.onlineHandler = onlineHandler;
        this.homeHandler = homeHandler;
        this.prepareHandler = prepareHandler;
        this.selectHandler = selectHandler;
        this.singleHandler = singleHandler;
        this.talkHandler = talkHandler;
        this.pageUtil = pageUtil;
        this.roomStateManager = roomStateManager;
        this.selectService = selectService;
        this.groupChatManager = groupChatManager;
        this.broadcastService = broadcastService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> data = objectMapper.readValue(message.getPayload(), Map.class);
        String type = (String) data.get("type");
        String pageType = (String) data.get("pageType");
        if ("connect".equals(type)) {
            String userCode = (String) data.get("userCode");
            sessionContextService.registerSession(session, userCode, pageType);
            return;
        }
        if ("page_change".equals(type)) {
            var context = sessionContextService.getSessionContext(session.getId());
            if (context != null) {
                String userCode = context.getUserCode();
                Integer roomCode = parseRoomCode(data.get("roomCode"));

                // Rebind room membership on refresh reconnect to avoid losing room_state updates.
                if (userCode != null && isRoomPage(pageType) && roomCode != null
                        && roomStateManager.getRoomCodeByUserCode(userCode) == null) {
                    selectService.joinRoom(roomCode, userCode);
                }

                pageUtil.syncPage(userCode, pageType);
            }
            return;
        }
        if("heartbeat".equals(type)) {
            sessionContextService.updateHeartbeat(session.getId());
            return;
        }
        // 群聊消息，按 pageType 分流：prepare 房间聊天 vs 全局群聊
        if ("group_chat_send".equals(type)) {
            var context = sessionContextService.getSessionContext(session.getId());
            if (context != null && context.getUserCode() != null) {
                String content = data.get("content") != null ? String.valueOf(data.get("content")).trim() : "";
                if (!content.isEmpty()) {
                    GroupChatMessageVO vo = new GroupChatMessageVO();
                    vo.setUserCode(context.getUserCode());
                    vo.setNickname(context.getNickname() != null ? context.getNickname() : context.getUserCode());
                    vo.setContent(content);
                    vo.setTimestamp(System.currentTimeMillis());

                    if ("prepare".equals(context.getPageType())) {
                        // 房间聊天：不持久化，只广播给同房间玩家
                        Integer roomCode = roomStateManager.getRoomCodeByUserCode(context.getUserCode());
                        if (roomCode != null) {
                            broadcastService.broadcastRoomChat(roomCode, vo);
                        }
                    } else {
                        // 全局群聊：持久化到 Redis + 广播给 home 用户
                        groupChatManager.addMessage(vo);
                        broadcastService.broadcastGroupChatMessage(vo);
                    }
                }
            }
            return;
        }
        if ("group_chat_history".equals(type)) {
            var context = sessionContextService.getSessionContext(session.getId());
            if (context != null && context.getUserCode() != null) {
                broadcastService.sendGroupChatHistory(context.getUserCode());
            }
            return;
        }
        // "join" 可从任意页面发出（例如 home 页好友列表点击加入房间）
        if("join".equals(type)) {
            var context = sessionContextService.getSessionContext(session.getId());
            if (context != null && context.getUserCode() != null) {
                Integer roomCode = parseRoomCode(data.get("roomCode"));
                if (roomCode != null) {
                    selectService.joinRoom(roomCode, context.getUserCode());
                }
            }
            return;
        }
        switch (pageType) {
            case "home" -> homeHandler.handleTextMessage(session, message);
            case "single" -> singleHandler.handleTextMessage(session, message);
            case "prepare" -> prepareHandler.handleTextMessage(session, message);
            case "select" -> selectHandler.handleTextMessage(session, message);
            case "online" -> onlineHandler.handleTextMessage(session, message);
            case "talk" -> talkHandler.handleTextMessage(session, message);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionContextService.removeSession(session.getId());
    }

    private Integer parseRoomCode(Object roomCode) {
        if (roomCode == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(roomCode));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isRoomPage(String pageType) {
        return "prepare".equals(pageType) || "online".equals(pageType);
    }
}