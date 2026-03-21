package com.example.snake_back.common.result;

import lombok.Data;

@Data // Lombok注解，自动生成get/set/toString等方法
public class Result<T> {
    /**
     * 状态码：1=成功，0=失败，其他=自定义错误码（比如2=参数错误，3=权限不足）
     */
    private Integer code;

    /**
     * 提示信息：成功时可为null，失败时返回具体原因（比如“用户名或密码错误”）
     */
    private String msg;

    /**
     * 数据体：成功时返回业务数据（比如登录的token+姓名），失败时可为null
     * 泛型T：适配任意数据类型（Map、List、单个实体、字符串等）
     */
    private T data;

    // ======================== 静态快捷方法（不用new，直接调用） ========================

    /**
     * 成功返回（无数据）
     * 示例：return Result.success();
     */
    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.setCode(1); // 成功状态码固定为1
        return result;
    }

    /**
     * 成功返回（带数据）
     * 示例：return Result.success(loginResultMap);
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(1);
        result.setData(data);
        return result;
    }

    /**
     * 失败返回（带提示信息）
     * 示例：return Result.error("用户名或密码错误");
     */
    public static <T> Result<T> error(String msg) {
        Result<T> result = new Result<>();
        result.setCode(0); // 失败状态码固定为0
        result.setMsg(msg);
        return result;
    }

    /**
     * 自定义返回（进阶用，比如自定义错误码）
     * 示例：return Result.build(2, "参数不能为空", null);
     */
    public static <T> Result<T> build(Integer code, String msg, T data) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }
}