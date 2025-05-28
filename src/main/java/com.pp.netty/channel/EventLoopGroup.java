package com.pp.netty.channel;

import com.pp.netty.util.concurrent.EventExecutorGroup;


/**
 * @Author: PP-jessica
 * @Description:事件循环组接口，既然引入了channelfuture，这里就可以多添加几个方法了
 */
public interface EventLoopGroup extends EventExecutorGroup {

    @Override
    EventLoop next();

    ChannelFuture register(Channel channel);


    ChannelFuture register(ChannelPromise promise);

}
