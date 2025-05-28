package com.pp.netty.util.concurrent;

import java.util.EventListener;

/**
 * @Author: PP-jessica
 * @Description:监听器的接口
 */
public interface GenericFutureListener<F extends Future<?>> extends EventListener {

    void operationComplete(F future) throws Exception;
}
