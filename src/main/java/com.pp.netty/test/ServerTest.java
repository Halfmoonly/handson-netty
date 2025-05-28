package com.pp.netty.test;

import com.pp.netty.bootstrap.ServerBootstrap;
import com.pp.netty.channel.nio.NioEventLoopGroup;
import com.pp.netty.util.concurrent.Future;
import com.pp.netty.util.concurrent.GenericFutureListener;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;

public class ServerTest {
    public static void main(String[] args) throws IOException, InterruptedException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(2);
         serverBootstrap.group(bossGroup,workerGroup).
                serverSocketChannel(serverSocketChannel).
                bind("127.0.0.1",8080).
                addListener(new GenericFutureListener<Future<? super Object>>() {
                            @Override
                             public void operationComplete(Future<? super Object> future) throws Exception {
                            System.out.println("监听器随后也执行了！");
                        }
                }).sync();
    }
}
