package com.pp.netty.channel;

import java.net.SocketAddress;

/**
 * @Author: PP-jessica
 * @Description:channel的顶级接口,暂时引入部分方法
 */
public interface Channel {

    ChannelId id();

    EventLoop eventLoop();


    Channel parent();


    ChannelConfig config();


    boolean isOpen();


    boolean isRegistered();


    boolean isActive();


    SocketAddress localAddress();


    SocketAddress remoteAddress();


    ChannelFuture closeFuture();

    /**
     * @Author: PP-jessica
     * @Description:该方法并不在此接口，而是在ChannelOutboundInvoker接口，现在先放在这里
     */
    ChannelFuture close();

    /**
     * @Author: PP-jessica
     * @Description:该方法并不在此接口，而是在ChannelOutboundInvoker接口，现在先放在这里
     */
    void bind(SocketAddress localAddress, ChannelPromise promise);

    /**
     * @Author: PP-jessica
     * @Description:该方法并不在此接口，而是在ChannelOutboundInvoker接口，现在先放在这里
     */
    void connect(SocketAddress remoteAddress, final SocketAddress localAddress,ChannelPromise promise);

    /**
     * @Author: PP-jessica
     * @Description:该方法并不在此接口，而是在unsafe接口，现在先放在这里
     */
    void register(EventLoop eventLoop, ChannelPromise promise);

    /**
     * @Author: PP-jessica
     * @Description:该方法并不在此接口，而是在unsafe接口，现在先放在这里
     */
    void beginRead();
}
