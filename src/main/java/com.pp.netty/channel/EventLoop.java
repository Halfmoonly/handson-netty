package com.pp.netty.channel;

import com.pp.netty.util.concurrent.EventExecutor;


/**
 * @Author: PP-jessica
 * @Description:事件循环组的接口
 */
public interface EventLoop extends EventExecutor, EventLoopGroup{

    @Override
    EventLoopGroup parent();
}
