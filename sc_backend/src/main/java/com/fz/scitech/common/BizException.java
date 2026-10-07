package com.fz.scitech.common;

/**
 * 业务异常:消息直接展示给用户
 */
public class BizException extends RuntimeException {

    public BizException(String message) {
        super(message);
    }
}
