package com.pp.netty.bootstrap;

import com.pp.netty.channel.EventLoopGroup;
import com.pp.netty.channel.nio.NioEventLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

public class Bootstrap {

    private static final Logger logger = LoggerFactory.getLogger(Bootstrap.class);

    private NioEventLoop nioEventLoop;

    private SocketChannel socketChannel;

    private EventLoopGroup workerGroup;

    public Bootstrap() {

    }

    public Bootstrap group(EventLoopGroup childGroup) {
        this.workerGroup = childGroup;
        return this;
    }

    public Bootstrap socketChannel(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
        return this;
    }

    public void connect(String inetHost, int inetPort) {
        connect(new InetSocketAddress(inetHost, inetPort));
    }


    public void connect(SocketAddress localAddress) {
        doConnect(localAddress);
    }

    private void doConnect(SocketAddress localAddress) {
        //获得单线程执行器
        nioEventLoop = (NioEventLoop)workerGroup.next().next();
        nioEventLoop.setSocketChannel(socketChannel);
        //注册任务先提交
        nioEventLoop.register(socketChannel,nioEventLoop);
        //然后再提交连接服务器任务
        doConnect0(localAddress);
    }

    private void doConnect0(SocketAddress localAddress) {
        nioEventLoop.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    socketChannel.connect(localAddress);
                    logger.info("客户端channel连接服务器成功了");
                } catch (Exception e) {
                    logger.error(e.getMessage());
                }
            }
        });
    }
}
