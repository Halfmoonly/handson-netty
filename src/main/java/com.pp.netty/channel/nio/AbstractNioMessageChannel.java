package com.pp.netty.channel.nio;

import com.pp.netty.channel.Channel;

import java.nio.channels.SelectableChannel;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractNioMessageChannel extends AbstractNioChannel {

    /**
     * @Author: PP-jessica
     * @Description:当该属性为true时，服务端将不再接受来自客户端的数据
     */
    boolean inputShutdown;

    protected AbstractNioMessageChannel(Channel parent, SelectableChannel ch, int readInterestOp) {
        super(parent, ch, readInterestOp);
    }

    @Override
    protected AbstractNioUnsafe newUnsafe() {
        return new NioMessageUnsafe();
    }

    @Override
    protected void doBeginRead() throws Exception {
        if (inputShutdown) {
            return;
        }
        super.doBeginRead();
    }

    private final class NioMessageUnsafe extends AbstractNioUnsafe {
        /**
         * @Author: PP-jessica
         * @Description:该属性也回到了正确的位置
         */
        private final List<Object> readBuf = new ArrayList<Object>();

        @Override
        public void read() {
            //该方法要在netty的线程执行器中执行
            assert eventLoop().inEventLoop(Thread.currentThread());
            boolean closed = false;
            Throwable exception = null;
            try {
                do {
                    //创建客户端的连接，存放在集合中
                    int localRead = doReadMessages(readBuf);
                    //返回值为0表示没有连接，直接退出即可
                    if (localRead == 0) {
                        break;
                    }
                } while (true);
            } catch (Throwable t) {
                exception = t;
            }
            int size = readBuf.size();
            for (int i = 0; i < size; i ++) {
                readPending = false;
                //把每一个客户端的channel注册到工作线程上,这里得不到workgroup，所以我们不在这里实现了，打印一下即可
                Channel child = (Channel) readBuf.get(i);
                System.out.println(child+"收到客户端的channel了");
                //TODO
            }
            //清除集合
            readBuf.clear();
            if (exception != null) {
                throw new RuntimeException(exception);
            }
        }

    }

    protected abstract int doReadMessages(List<Object> buf) throws Exception;
}
