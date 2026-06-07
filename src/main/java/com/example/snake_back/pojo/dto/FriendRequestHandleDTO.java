package com.example.snake_back.pojo.dto;

public class FriendRequestHandleDTO {
    private String requestId;
    private String action; // accept / reject

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
}