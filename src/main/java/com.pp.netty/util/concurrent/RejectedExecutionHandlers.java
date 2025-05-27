package com.pp.netty.util.concurrent;

import java.util.concurrent.RejectedExecutionException;

/**
 * @Author: PP-jessica
 * @Description:创建拒绝策略处理器
 */
public class RejectedExecutionHandlers {

    private static final RejectedExecutionHandler REJECT = new RejectedExecutionHandler() {
        @Override
        public void rejected(Runnable task, SingleThreadEventExecutor executor) {
            throw new RejectedExecutionException();
        }
    };

    private RejectedExecutionHandlers() { }


    public static RejectedExecutionHandler reject() {
        return REJECT;
    }

}
