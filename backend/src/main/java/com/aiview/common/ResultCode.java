package com.aiview.common;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(0, "ok"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    USERNAME_EXISTS(1001, "用户名已存在"),
    LOGIN_FAILED(1002, "用户名或密码错误"),
    TOKEN_INVALID(1003, "token 无效"),

    INTERVIEW_NOT_FOUND(2001, "面试会话不存在"),
    INTERVIEW_STATE_ERROR(2002, "当前状态不允许该操作"),
    INTERVIEW_FINISHED(2003, "面试已结束"),
    AI_SERVICE_ERROR(2004, "AI 服务调用失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}