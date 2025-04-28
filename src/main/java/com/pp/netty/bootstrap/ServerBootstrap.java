package com.pp.netty.bootstrap;

import com.pp.netty.channel.nio.NioEventLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;

public class ServerBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(ServerBootstrap.class);

    private NioEventLoop nioEventLoop;

    private ServerSocketChannel serverSocketChannel;

    public ServerBootstrap() {

    }

    public ServerBootstrap nioEventLoop(NioEventLoop nioEventLoop) {
        this.nioEventLoop = nioEventLoop;
        return this;
    }

    public ServerBootstrap serverSocketChannel(ServerSocketChannel serverSocketChannel) {
        this.serverSocketChannel = serverSocketChannel;
        return this;
    }

    public void bind(String host,int inetPort) {
        bind(new InetSocketAddress(host,inetPort));
    }

    public void bind(SocketAddress localAddress) {
        doBind(localAddress);
    }

    private void doBind(SocketAddress localAddress) {
        //注册事件并把轮询线程跑起来: 重载方法register(ServerSocketChannel channel, NioEventLoop nioEventLoop)
        nioEventLoop.register(serverSocketChannel,this.nioEventLoop);
        //服务器后绑定端口，这时候执行器的线程已经启动了
        doBind0(localAddress);
    }

    /**
     * @Author: PP-jessica
     * @Description:这里把绑定端口号封装成一个runnable，提交到单线程执行器的任务队列，绑定端口号仍然由单线程执行器完成
     * 这时候执行器的线程已经启动了
     */
    private void doBind0(SocketAddress localAddress) {
        nioEventLoop.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocketChannel.bind(localAddress);
                    logger.info("服务端channel和端口号绑定了");
                } catch (Exception e) {
                    logger.error(e.getMessage());
                }
            }
        });
    }
}
