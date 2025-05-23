package com.pp.netty.bootstrap;

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

    public Bootstrap() {

    }

    public Bootstrap nioEventLoop(NioEventLoop nioEventLoop) {
        this.nioEventLoop = nioEventLoop;
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
        //先注册事件并把轮询线程跑起来: 重载方法register(SocketChannel channel,NioEventLoop nioEventLoop)
        nioEventLoop.register(socketChannel,this.nioEventLoop);
        //客户端后绑定连接，这时候执行器的线程已经启动了
        doConnect0(localAddress);
    }

    private void doConnect0(SocketAddress localAddress) {
        nioEventLoop.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    socketChannel.connect(localAddress);
                } catch (Exception e) {
                    logger.error(e.getMessage());
                }
            }
        });
    }
}
