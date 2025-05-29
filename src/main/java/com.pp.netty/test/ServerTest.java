package com.pp.netty.test;

import com.pp.netty.bootstrap.ServerBootstrap;
import com.pp.netty.channel.Channel;
import com.pp.netty.channel.ChannelFuture;
import com.pp.netty.channel.nio.NioEventLoopGroup;
import com.pp.netty.channel.socket.NioServerSocketChannel;

import java.io.IOException;

public class ServerTest {
    public static void main(String[] args) throws IOException, InterruptedException {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(2);
        ChannelFuture channelFuture = serverBootstrap.group(bossGroup,workerGroup).
                channel(NioServerSocketChannel.class).
                bind(8080).addListener(future -> System.out.println("我绑定成功了")).sync();
        Channel channel = channelFuture.channel();
        //        NioServerSocketChannel channel = new NioServerSocketChannel();
       // AbstractNioChannel.NioUnsafe unsafe = (AbstractNioChannel.NioUnsafe) channel.unsafe();
//        channel.writeAndFlush()
//        socketChannel.write(ByteBuffer.wrap("我还不是netty，但我知道你上线了".getBytes()));
    }
}
