package com.pp.netty.channel.nio;


import com.pp.netty.channel.EventLoopTaskQueueFactory;
import com.pp.netty.channel.SelectStrategy;
import com.pp.netty.channel.SingleThreadEventLoop;
import com.pp.netty.util.concurrent.RejectedExecutionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @Author: PP-jessica
 * @Description:该类就是真正执行循环事件的类，nio中selector轮询事件，包括处理事件，都在该类中进行
 */
public class NioEventLoop extends SingleThreadEventLoop {

    private static final Logger logger = LoggerFactory.getLogger(NioEventLoop.class);

    private final Selector selector;

    private final SelectorProvider provider;

    private  SelectStrategy selectStrategy;

    NioEventLoop(NioEventLoopGroup parent, Executor executor, SelectorProvider selectorProvider,
                 SelectStrategy strategy, RejectedExecutionHandler rejectedExecutionHandler,
                 EventLoopTaskQueueFactory queueFactory) {
        super(parent, executor, false, newTaskQueue(queueFactory), newTaskQueue(queueFactory),
                rejectedExecutionHandler);
        if (selectorProvider == null) {
            throw new NullPointerException("selectorProvider");
        }
        if (strategy == null) {
            throw new NullPointerException("selectStrategy");
        }
        provider = selectorProvider;
        selector = openSecector();
        selectStrategy = strategy;
    }

    private static Queue<Runnable> newTaskQueue(
            EventLoopTaskQueueFactory queueFactory) {
        if (queueFactory == null) {
            return new LinkedBlockingQueue<Runnable>(DEFAULT_MAX_PENDING_TASKS);
        }
        return queueFactory.newTaskQueue(DEFAULT_MAX_PENDING_TASKS);
    }

    //得到用于轮询的选择器
    private Selector openSecector() {
        //未包装过的选择器
        final Selector unwrappedSelector;
        try {
            unwrappedSelector = provider.openSelector();
            return unwrappedSelector;
        } catch (IOException e) {
            throw new RuntimeException("failed to open a new selector", e);
        }
    }

    public Selector unwrappedSelector() {
        return selector;
    }

    /**
     * @Author: PP-jessica
     * @Description:在这里进行选择器的轮询
     */
    @Override
    @SuppressWarnings("InfiniteLoopStatement")
    protected void run() {
        for (;;) {
            try {
                //没有事件就阻塞在这里
                select();
                //如果有事件,就处理就绪事件
                processSelectedKeys();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
            //执行单线程执行器中的所有任务
            runAllTasks();
            }
        }
    }

    private void select() throws IOException {
        Selector selector = this.selector;
        //这里是一个死循环
        for (;;){
            //如果没有就绪事件，就在这里阻塞3秒
            int selectedKeys = selector.select(1000);
            //如果有事件或者单线程执行器中有任务待执行，就退出循环
            if (selectedKeys != 0 || hasTasks()) {
                break;
            }
        }
    }

    private void processSelectedKeys() throws Exception {
        //采用优化过后的方式处理事件,Netty默认会采用优化过的Selector对就绪事件处理。
        //processSelectedKeysOptimized();
        //未优化过的处理事件方式
        processSelectedKeysPlain(selector.selectedKeys());
    }

    private void processSelectedKeysPlain(Set<SelectionKey> selectedKeys) throws Exception {
        if (selectedKeys.isEmpty()) {
            return;
        }
        Iterator<SelectionKey> i = selectedKeys.iterator();
        for (;;) {
            final SelectionKey k = i.next();
            //还记得channel在注册时的第三个参数this吗？这里通过attachment方法就可以得到nio类的channel
            final Object a = k.attachment();
            i.remove();
            //处理就绪事件
            if (a instanceof AbstractNioChannel) {
                processSelectedKey(k,(AbstractNioChannel) a);
            }
            if (!i.hasNext()) {
                break;
            }
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:既然都引入了channel，那么nioeventloop也可以和socketChannel，serverSocketChannel解耦了
     * 这里要重写该方法，现在应该发现了，AbstractNioChannel作为抽象类，既可以调用服务端channel的方法，也可以调用客户端channel的
     * 方法，这就巧妙的把客户端和服务端的channel与nioEventLoop解耦了
     */
    private void processSelectedKey(SelectionKey k,AbstractNioChannel ch) throws Exception {
        try {
            //获取Unsafe类
            final AbstractNioChannel.NioUnsafe unsafe = ch.unsafe();
            //得到key感兴趣的事件
            int ops = k.interestOps();
            //如果是连接事件
            if (ops == SelectionKey.OP_CONNECT) {
                //移除连接事件，否则会一直通知，这里实际上是做了个减法。位运算的门道，我们会放在之后和线程池的状态切换一起讲
                //这里先了解就行
                ops &= ~SelectionKey.OP_CONNECT;
                //重新把感兴趣的事件注册一下
                k.interestOps(ops);
                //然后再注册客户端channel感兴趣的读事件
                ch.doBeginRead();
            }
            //如果是读事件，不管是客户端还是服务端的，都可以直接调用read方法
            //这时候一定要记清楚，NioSocketChannel和NioServerSocketChannel并不会纠缠
            //用户创建的是哪个channel，这里抽象类调用就是它的方法
            //如果不明白，那么就找到AbstractNioChannel的方法看一看，想一想，虽然那里传入的参数是this，但传入的并不是抽象类本身，想想你创建的
            //是NioSocketChannel还是NioServerSocketChannel，是哪个，传入的就是哪个。只不过在这里被多态赋值给了抽象类
            //创建的是子类对象，但在父类中调用了this，得到的仍然是子类对象
            if (ops ==  SelectionKey.OP_READ) {
                unsafe.read();
            }
            if (ops == SelectionKey.OP_ACCEPT) {
                unsafe.read();
            }
        } catch (CancelledKeyException ignored) {
            throw new RuntimeException(ignored.getMessage());
        }
    }
}

