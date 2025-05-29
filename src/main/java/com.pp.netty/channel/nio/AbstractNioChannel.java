package com.pp.netty.channel.nio;

import com.pp.netty.channel.AbstractChannel;
import com.pp.netty.channel.Channel;
import com.pp.netty.channel.ChannelPromise;
import com.pp.netty.channel.EventLoop;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

public abstract class AbstractNioChannel extends AbstractChannel {

    //该抽象类是serversocketchannel和socketchannel的公共父类
    private final SelectableChannel ch;

    //channel要关注的事件
    protected final int readInterestOp;

    //channel注册到selector后返回的key
    volatile SelectionKey selectionKey;

    //是否还有未读取的数据
    boolean readPending;


    protected AbstractNioChannel(Channel parent, SelectableChannel ch, int readInterestOp) {
        super(parent);
        this.ch = ch;
        this.readInterestOp = readInterestOp;
        try {
            //设置服务端channel为非阻塞模式
            ch.configureBlocking(false);
        } catch (IOException e) {
            try {
                //有异常直接关闭channel
                ch.close();
            } catch (IOException e2) {
                throw new RuntimeException(e2);
            }
            throw new RuntimeException("Failed to enter non-blocking mode.", e);
        }
    }

    @Override
    public boolean isOpen() {
        return ch.isOpen();
    }

    @Override
    public NioUnsafe unsafe() {
        return (NioUnsafe) super.unsafe();
    }

    protected SelectableChannel javaChannel() {
        return ch;
    }


    @Override
    public NioEventLoop eventLoop() {
        return (NioEventLoop) super.eventLoop();
    }

    protected SelectionKey selectionKey() {
        assert selectionKey != null;
        return selectionKey;
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof NioEventLoop;
    }


    public interface NioUnsafe extends Unsafe {

        SelectableChannel ch();

        void finishConnect();

        void read();

        void forceFlush();
    }

    /**
     * @Author: PP-jessica
     * @Description:终于又引入了一个unsafe的抽象内部类
     */
    protected abstract class AbstractNioUnsafe extends AbstractUnsafe implements NioUnsafe {

        @Override
        public final SelectableChannel ch() {
            return javaChannel();
        }

        /**
         * @Author: PP-jessica
         * @Description:该方法回到了原本的位置
         */
        @Override
        public final void connect(final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
            try {
                boolean doConnect = doConnect(remoteAddress, localAddress);
                if (!doConnect) {
                    promise.trySuccess();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * @Author: PP-jessica
         * @Description:暂时不做实现
         */
        @Override
        public final void finishConnect() {}

        @Override
        public final void forceFlush() {}
    }

    @Override
    protected void doRegister() throws Exception {
        //在这里把channel注册到单线程执行器中的selector上,注意这里的第三个参数this，这意味着channel注册的时候把本身，也就是nio类的channel
        //当作附件放到key上了，之后会用到这个。
        selectionKey = javaChannel().register(eventLoop().unwrappedSelector(), 0, this);
    }


    @Override
    protected void doBeginRead() throws Exception {
        final SelectionKey selectionKey = this.selectionKey;
        //检查key是否是有效的
        if (!selectionKey.isValid()) {
            return;
        }
        //还没有设置感兴趣的事件，所以得到的值为0
        final int interestOps = selectionKey.interestOps();
        //interestOps中并不包含readInterestOp
        if ((interestOps & readInterestOp) == 0) {
            //设置channel关注的事件，读事件
            selectionKey.interestOps(interestOps | readInterestOp);
        }
    }

    protected abstract boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception;

    /**
     * @Author: PP-jessica
     * @Description:该方法先不实现，在引入了channelHandler后会实现
     */
    @Override
    protected void doClose() throws Exception {}

}
