package com.pp.netty.channel.nio;


import com.pp.netty.channel.EventLoopTaskQueueFactory;
import com.pp.netty.channel.SingleThreadEventLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * @Author: PP-jessica
 * @Description:该类就是真正执行循环事件的类，nio中selector轮询事件，包括处理事件，都在该类中进行
 */
public class NioEventLoop extends SingleThreadEventLoop {

    private static final Logger logger = LoggerFactory.getLogger(NioEventLoop.class);

    private final ServerSocketChannel serverSocketChannel;

    private final SocketChannel socketChannel;

    /**
     * @Author: PP-jessica
     * @Description:这个属性是暂时的，后面我们会把它从该类中剔除
     */
    private NioEventLoop worker;

    private final Selector selector;

    private final SelectorProvider provider;

    public NioEventLoop(ServerSocketChannel serverSocketChannel,SocketChannel socketChannel) {
        this(null,SelectorProvider.provider(), null,serverSocketChannel, socketChannel);
    }
    public NioEventLoop(Executor executor, SelectorProvider selectorProvider, EventLoopTaskQueueFactory queueFactory,
                        ServerSocketChannel serverSocketChannel,SocketChannel socketChannel) {
        super(executor,queueFactory);
        if (selectorProvider == null) {
            throw new NullPointerException("selectorProvider");
        }
        if (serverSocketChannel != null && socketChannel != null) {
            throw new RuntimeException("only one channel can be here! server or client!");
        }
        this.provider = selectorProvider;
        this.serverSocketChannel = serverSocketChannel;
        this.socketChannel = socketChannel;
        this.selector = openSecector();
    }

    public void setWorker(NioEventLoop worker) {
        this.worker = worker;
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
                //没有事件就阻塞在这里，回应NIO中得while(iterator.hasNext())
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
            int selectedKeys = selector.select(3000);
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
            i.remove();
            //处理就绪事件
            processSelectedKey(k);
            if (!i.hasNext()) {
                break;
            }
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:此时，应该也可以意识到，在不参考netty源码的情况下编写该方法，直接传入serverSocketChannel或者
     * socketChannel参数，每一次都要做几步判断，因为单线程的执行器是客户端和服务端通用的，所以你不知道传进来的参数究竟是
     * 什么类型的channel，那么复杂的判断就必不可少了，代码也就变得丑陋。。这种情况，实际上应该想到完美的解决方法了，
     * 就是使用反射，传入Class，用工厂反射创建对象。netty中就是这么做的。
     */
    private void processSelectedKey(SelectionKey k) throws Exception {
        //说明传进来的是客户端channel，要处理客户端的事件
        if (socketChannel != null) {
            if (k.isConnectable()) {
                //channel已经连接成功
                if (socketChannel.finishConnect()) {
                    //注册读事件
                    socketChannel.register(selector,SelectionKey.OP_READ);
                }
            }
            //如果是读事件
            if (k.isReadable()) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                int len = socketChannel.read(byteBuffer);
                byte[] buffer = new byte[len];
                byteBuffer.flip();
                byteBuffer.get(buffer);
                logger.info("客户端收到消息:{}",new String(buffer));
            }
            return;
        }
        //运行到这里说明是服务端的channel
        if (serverSocketChannel != null) {
            //连接事件
            if (k.isAcceptable()) {
                SocketChannel socketChannel = serverSocketChannel.accept();
                socketChannel.configureBlocking(false);
                //由worker执行器去执行注册
                worker.registerRead(socketChannel,worker);
                socketChannel.write(ByteBuffer.wrap("我还不是netty，但我知道你上线了".getBytes()));
                logger.info("服务器发送消息成功！");
            }
            if (k.isReadable()) {
                SocketChannel channel = (SocketChannel)k.channel();
                ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                int len = channel.read(byteBuffer);
                if (len == -1) {
                    logger.info("客户端通道要关闭！");
                    channel.close();
                    return;
                }
                byte[] bytes = new byte[len];
                byteBuffer.flip();
                byteBuffer.get(bytes);
                logger.info("收到客户端发送的数据:{}",new String(bytes));
            }
        }
    }
}

