package com.pp.netty.test;

import com.pp.netty.bootstrap.Bootstrap;
import com.pp.netty.channel.nio.NioEventLoopGroup;
import com.pp.netty.channel.socket.NioSocketChannel;

import java.io.IOException;

public class ClientTest {
    public static void main(String[] args) throws IOException {
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(1);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup).
                channel(NioSocketChannel.class);
        bootstrap.connect("127.0.0.1",8080);
    }

}
