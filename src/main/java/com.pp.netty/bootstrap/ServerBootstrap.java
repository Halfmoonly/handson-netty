package com.pp.netty.bootstrap;

import com.pp.netty.channel.EventLoopGroup;
import com.pp.netty.channel.nio.NioEventLoop;
import com.pp.netty.util.concurrent.DefaultPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;

public class ServerBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(ServerBootstrap.class);

    private EventLoopGroup bossGroup;

    private EventLoopGroup workerGroup;

    private NioEventLoop nioEventLoop;

    private ServerSocketChannel serverSocketChannel;

    public ServerBootstrap() {

    }

    public ServerBootstrap group(EventLoopGroup parentGroup, EventLoopGroup childGroup) {
        this.bossGroup = parentGroup;
        this.workerGroup = childGroup;
        return this;
    }

    public ServerBootstrap serverSocketChannel(ServerSocketChannel serverSocketChannel) {
        this.serverSocketChannel = serverSocketChannel;
        return this;
    }


    /**
     * @Author: PP-jessica
     * @Description:这里只对服务端做一下修改，客户端就暂且不修改了，看明白即可
     */
    public DefaultPromise<Object> bind(String host, int inetPort) {
        return bind(new InetSocketAddress(host, inetPort));
    }

    public DefaultPromise<Object> bind(SocketAddress localAddress) {
        return doBind(localAddress);
    }

    private DefaultPromise<Object> doBind(SocketAddress localAddress) {
        //得到boss事件循环组中的事件执行器，也就是单线程执行器,这个里面其实就包含一个单线程执行器，在workergroup中才包含多个单线程执行器
        //这里就暂时先写死了
        nioEventLoop =(NioEventLoop)bossGroup.next().next();
        nioEventLoop.setServerSocketChannel(serverSocketChannel);
        nioEventLoop.setWorkerGroup(workerGroup);
        //直接使用nioeventloop把服务端的channel注册到单线程执行器上
        //应该也把register改成返回promise的，下节课就会实现了
        nioEventLoop.register(serverSocketChannel,nioEventLoop);
        DefaultPromise<Object> defaultPromise = new DefaultPromise<>(nioEventLoop);
        doBind0(localAddress,defaultPromise);
        return defaultPromise;
    }

    /**
     * @Author: PP-jessica
     * @Description:这里把绑定端口号封装成一个runnable，提交到单线程执行器的任务队列，绑定端口号仍然由单线程执行器完成
     * 这时候执行器的线程已经启动了
     */
    private void doBind0(SocketAddress localAddress,DefaultPromise<Object> defaultPromise) {
        nioEventLoop.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocketChannel.bind(localAddress);
                    Thread.sleep(5000);
                    defaultPromise.setSuccess(null);
                } catch (Exception e) {
                    logger.error(e.getMessage());
                }
            }
        });
    }
}
