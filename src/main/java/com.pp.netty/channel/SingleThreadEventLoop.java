package com.pp.netty.channel;

import com.pp.netty.util.concurrent.RejectedExecutionHandler;
import com.pp.netty.util.concurrent.SingleThreadEventExecutor;
import com.pp.netty.util.internal.ObjectUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.Executor;


/**
 * @Author: PP-jessica
 * @Description:单线程事件循环，只要在netty中见到eventloop，就可以把该类视为线程类
 */
public abstract class SingleThreadEventLoop extends SingleThreadEventExecutor implements EventLoop{

    private static final Logger logger = LoggerFactory.getLogger(SingleThreadEventLoop.class);

    //任务队列的容量，默认是Integer的最大值
    protected static final int DEFAULT_MAX_PENDING_TASKS = Integer.MAX_VALUE;


    protected SingleThreadEventLoop(EventLoopGroup parent, Executor executor,
                                    boolean addTaskWakesUp, Queue<Runnable> taskQueue, Queue<Runnable> tailTaskQueue,
                                    RejectedExecutionHandler rejectedExecutionHandler) {
        super(parent, executor, addTaskWakesUp, taskQueue, rejectedExecutionHandler);
    }

    /**
     * @Author: PP-jessica
     * @Description:下面这两个方法并不是在这里实现的，写在这里也只是暂时的
     */
    @Override
    public EventLoopGroup parent() {
        return null;
    }

    @Override
    public EventLoop next() {
        return this;
    }

    @Override
    protected boolean hasTasks() {
        return super.hasTasks();
    }

    /**
     * @Author: PP-jessica
     * @Description:因为没有和ServerSocketChannel，SocketChannel解耦，
     * 这里原本是几个重载的注册方法。现在可以把这几个方法变成一个了
     */
    @Override
    public ChannelFuture register(Channel channel) {
        //在这里可以发现在执行任务的时候，channel和promise也是绑定的
        return register(new DefaultChannelPromise(channel, this));
    }

    /**
     * @Author: PP-jessica
     * @Description:因为还没有引入unsafe类，所以该方法暂时先简化实现
     */
    @Override
    public ChannelFuture register(final ChannelPromise promise) {
        ObjectUtil.checkNotNull(promise, "promise");
        promise.channel().unsafe().register(this, promise);
        return promise;
    }

}
