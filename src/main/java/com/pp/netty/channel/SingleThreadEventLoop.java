package com.pp.netty.channel;

import com.pp.netty.channel.nio.NioEventLoop;
import com.pp.netty.util.concurrent.DefaultThreadFactory;
import com.pp.netty.util.concurrent.SingleThreadEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;


/**
 * @Author: PP-jessica
 * @Description:单线程事件循环，只要在netty中见到eventloop，就可以把该类视为线程类
 */
public abstract class SingleThreadEventLoop extends SingleThreadEventExecutor {

    private static final Logger logger = LoggerFactory.getLogger(SingleThreadEventLoop.class);

    //任务队列的容量，默认是Integer的最大值
    protected static final int DEFAULT_MAX_PENDING_TASKS = Integer.MAX_VALUE;


    protected SingleThreadEventLoop(Executor executor,EventLoopTaskQueueFactory queueFactory) {
        super(executor,queueFactory,new DefaultThreadFactory());
    }

    @Override
    protected boolean hasTasks() {
        return super.hasTasks();
    }

    /**
     * @Author: PP-jessica
     * @Description:在这里把channel绑定到单线程执行器上,实际上就是把channel注册到执行器中的selector上，因为不知道
     * 传入的参数是ServerSocketChannel还是SocketChannel，所以该方法要做重载，但在netty源码中则无必要。随着进度的更新，我们的代码
     * 也会渐渐向netty靠拢
     */
    public void register(ServerSocketChannel channel, NioEventLoop nioEventLoop) {
        //如果执行该方法的线程就是执行器中的线程，直接执行方法即可
        if (inEventLoop(Thread.currentThread())) {
            register0(channel,nioEventLoop);
        }else {
            //在这里，第一次向单线程执行器中提交任务的时候，执行器终于开始执行了
            nioEventLoop.execute(new Runnable() {
                @Override
                public void run() {
                    register0(channel,nioEventLoop);
                    logger.info("服务器的channel已注册到多路复用器上了！:{}",Thread.currentThread().getName());
                }
            });
        }
    }

    public void register(SocketChannel channel,NioEventLoop nioEventLoop) {
        //如果执行该方法的线程就是执行器中的线程，直接执行方法即可
        if (inEventLoop(Thread.currentThread())) {
            register0(channel,nioEventLoop);
        }else {
            //在这里，第一次向单线程执行器中提交任务的时候，执行器终于开始执行了
            nioEventLoop.execute(new Runnable() {
                @Override
                public void run() {
                    register0(channel,nioEventLoop);
                    logger.info("客户端的channel已注册到多路复用器上了！:{}",Thread.currentThread().getName());
                }
            });
        }
    }

    public void registerRead(SocketChannel channel,NioEventLoop nioEventLoop) {
        //如果执行该方法的线程就是执行器中的线程，直接执行方法即可
        if (inEventLoop(Thread.currentThread())) {
            register0(channel,nioEventLoop);
        }else {
            //在这里，第一次向单线程执行器中提交任务的时候，执行器终于开始执行了
            nioEventLoop.execute(new Runnable() {
                @Override
                public void run() {
                    register00(channel,nioEventLoop);
                    logger.info("客户端的channel已注册到多路复用器上了！:{}",Thread.currentThread().getName());
                }
            });
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:客户端启动类调用该方法注册channel到selector上，注册的是连接事件
     */
    private void register0(SocketChannel channel,NioEventLoop nioEventLoop) {
        try {
            channel.configureBlocking(false);
            channel.register(nioEventLoop.unwrappedSelector(), SelectionKey.OP_CONNECT);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:该方法是特意为服务端接收到客户端channel，然后将channel注册到sleector而重载的
     */
    private void register00(SocketChannel channel,NioEventLoop nioEventLoop) {
        try {
            channel.configureBlocking(false);
            channel.register(nioEventLoop.unwrappedSelector(), SelectionKey.OP_READ);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    private void register0(ServerSocketChannel channel,NioEventLoop nioEventLoop) {
        try {
            channel.configureBlocking(false);
            channel.register(nioEventLoop.unwrappedSelector(), SelectionKey.OP_ACCEPT);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }
}
