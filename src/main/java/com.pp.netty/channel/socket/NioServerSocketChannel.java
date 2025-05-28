package com.pp.netty.channel.socket;


import com.pp.netty.channel.nio.AbstractNioMessageChannel;
import com.pp.netty.channel.nio.NioEventLoop;
import com.pp.netty.util.internal.SocketUtils;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.List;

/**
 * @Author: PP-jessica
 * @Description:对serversocketchannel做了一层包装，同时也因为channel接口和抽象类的引入，终于可以使NioEventLoop和channel解耦了
 */
public class NioServerSocketChannel extends AbstractNioMessageChannel {

    //在无参构造器被调用的时候，该成员变量就被创建了
    private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();


    private static ServerSocketChannel newSocket(SelectorProvider provider) {
        try {
            return provider.openServerSocketChannel();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open a server socket.", e);
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:无参构造，当调用该构造器的时候，会调用到静态方法newSocket，返回一个ServerSocketChannel
     */
    public NioServerSocketChannel() {
        this(newSocket(DEFAULT_SELECTOR_PROVIDER));
    }

    public NioServerSocketChannel(ServerSocketChannel channel) {
        //创建的为NioServerSocketChannel时，没有父类channel，SelectionKey.OP_ACCEPT是服务端channel的关注事件
        super(null, channel, SelectionKey.OP_ACCEPT);
    }

    @Override
    public boolean isActive() {
        return isOpen() && javaChannel().socket().isBound();
    }

    @Override
    protected ServerSocketChannel javaChannel() {
        return (ServerSocketChannel) super.javaChannel();
    }

    @Override
    public NioEventLoop eventLoop() {
        return (NioEventLoop) super.eventLoop();
    }

    /**
     * @Author: PP-jessica
     * @Description:这里做空实现即可，服务端的channel并不会做连接动作
     */
    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        //这里是一个系统调用方法，判断当前的java版本是否为7以上，这里我就直接写死了，不引入更多的工具类了
        //如果用户没有设置backlog参数，config.getBacklog()点进去看源码最后发现会该值会在NetUtil的静态代码块中被赋值，windows环境下值为200
        //linux环境下默认为128。Backlog可以设置全连接队列的大小，控制服务端接受连接的数量。
        //这里就直接写死了，还没有引入channelconfig配置类
        javaChannel().bind(localAddress, 128);
        if (isActive()) {
            System.out.println("服务端绑定端口成功");
            doBeginRead();
        }
//        } else {
//            javaChannel().socket().bind(localAddress, config.getBacklog());
//        }
    }

    protected void doClose() throws Exception {
        javaChannel().close();
    }


    /**
     * @Author: PP-jessica
     * @Description:该方法是服务端channel接受连接的方法
     */
    @Override
    protected int doReadMessages(List<Object> buf) throws Exception {
        //有连接进来，创建出java原生的客户端channel
        SocketChannel ch = SocketUtils.accept(javaChannel());
        try {
            if (ch != null) {
                //创建niosocketchannel
                buf.add(new NioSocketChannel(this, ch));
                return 1;
            }
        } catch (Throwable t) {
           t.printStackTrace();
            try {
                //有异常则关闭客户端的channel
                ch.close();
            } catch (Throwable t2) {
                throw new RuntimeException("Failed to close a socket.", t2);
            }
        }
        return 0;
    }
}
