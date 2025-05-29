package com.pp.netty.channel.nio;

import com.pp.netty.channel.Channel;

import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

public abstract class AbstractNioByteChannel extends AbstractNioChannel{

    protected AbstractNioByteChannel(Channel parent, SelectableChannel ch) {
        super(parent, ch, SelectionKey.OP_READ);
    }

    /**
     * @Author: PP-jessica
     * @Description:由于还未引入unsafe类，所以该方法直接定义在这里，先不设定接口
     * 这个是客户端channel读取数据的方法
     */
    @Override
    public final void read() {
        //暂时用最原始简陋的方法处理
        ByteBuffer byteBuf = ByteBuffer.allocate(1024);
        try {
            doReadBytes(byteBuf);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected abstract int doReadBytes(ByteBuffer buf) throws Exception;
}
