package com.pp.netty.util.concurrent;


/**
 * @Author: PP-jessica
 * @Description:eventloop的接口，这个接口也继承了EventExecutorGroup，这样在eventloopgroup中
 * 调用方法，eventloop中就可以直接调用同名方法。
 */
public interface EventExecutor extends EventExecutorGroup {

    @Override
    EventExecutor next();

    EventExecutorGroup parent();

    boolean inEventLoop(Thread thread);
}
