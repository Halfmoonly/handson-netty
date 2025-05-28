package com.pp.netty.test;

import com.pp.netty.bootstrap.Bootstrap;
import com.pp.netty.channel.nio.NioEventLoopGroup;

import java.io.IOException;
import java.nio.channels.SocketChannel;

/**
 * @Author: PP-jessica
 * @Description:这节课的代码只更新了服务端的，因为只是添加了一个小东西，所以，客户端就不更新了。等讲到channel，promise编程channelfuture是
 * 客户端和服务端会一同更新
 */
public class ClientTest {
    public static void main(String[] args) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(1);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup).
                socketChannel(socketChannel);
        bootstrap.connect("127.0.0.1",8080);
    }

}
