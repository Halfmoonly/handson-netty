package com.pp.netty.channel;


import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

/**
 * @Author: PP-jessica
 * @Description:channel接口的抽象实现类，这里面有许多重要方法没有实现，有很多方法没有引进，接下来的几节课会依次引入
 * 该类中的bind，close等等方法，都是定义好的模版方法，在子类中有真正的被调用的实现方法，以doxxxx开头。
 */
public abstract class AbstractChannel implements Channel{

    /**
     * @Author: PP-jessica
     * @Description:当创建的是客户端channel时，parent为serversocketchannel
     * 如果创建的为服务端channel，parent则为null
     */
    private final Channel parent;

    private final ChannelId id;
    /**
     * @Author: PP-jessica
     * @Description:看名字也可以猜出，这个future是在channel关闭的时候使用的，是一个静态内部类
     */
    private final CloseFuture closeFuture = new CloseFuture(this);

    private volatile SocketAddress localAddress;

    private volatile SocketAddress remoteAddress;

    private Throwable initialCloseCause;

    /**
     * @Author: PP-jessica
     * @Description:每一个channel都要绑定到一个eventloop上
     */
    private volatile EventLoop eventLoop;

    /**
     * @Author: PP-jessica
     * @Description:该channel是否注册过
     */
    private volatile boolean registered;

    protected AbstractChannel(Channel parent) {
        this.parent = parent;
        id = newId();
    }


    protected AbstractChannel(Channel parent, ChannelId id) {
        this.parent = parent;
        this.id = id;
    }

    @Override
    public final ChannelId id() {
        return id;
    }

    @Override
    public EventLoop eventLoop() {
        EventLoop eventLoop = this.eventLoop;
        if (eventLoop == null) {
            throw new IllegalStateException("channel not registered to an event loop");
        }
        return eventLoop;
    }

    @Override
    public Channel parent() {
        return parent;
    }

    @Override
    public ChannelConfig config() {
        return null;
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public SocketAddress localAddress() {
        return null;
    }

    @Override
    public SocketAddress remoteAddress() {
        return null;
    }

    @Override
    public ChannelFuture closeFuture() {
        return closeFuture;
    }

    @Override
    public ChannelFuture close() {
        return null;
    }

    protected ChannelId newId() {
        return DefaultChannelId.newInstance();
    }

    protected abstract boolean isCompatible(EventLoop loop);


    /**
     * @Author: PP-jessica
     * @Description:该方法并不在此类，而是在该类的静态内部类AbstractUnsafe中，现在先放在这里
     * 在这里多说一句，越到后面抽象类越多，看源码的时候常常会发现抽象父类调用子类的方法看着看着就晕了，我最开始学看源码的时候就这样
     * 后来看得多了，我给自己总结了一句话：看抽象类的时候只要记住我们最终创建的那个类是各个抽象类和各种接口的最终子类，一直记着这句话
     * 看源码时候就会清楚很多，不管抽象父类怎么调用子类方法，实际上都是在我们创建的最终子类中调来调去。
     */
    @Override
    public final void register(EventLoop eventLoop, final ChannelPromise promise) {
        if (eventLoop == null) {
            throw new NullPointerException("eventLoop");
        }
        //检查channel是否注册过，注册过就手动设置promise失败
        if (isRegistered()) {
            promise.setFailure(new IllegalStateException("registered to an event loop already"));
            return;
        }
        //判断当前使用的执行器是否为NioEventLoop，如果不是手动设置失败
        if (!isCompatible(eventLoop)) {
            promise.setFailure(new IllegalStateException("incompatible event loop type: " + eventLoop.getClass().getName()));
            return;
        }
        //稍微学过netty的人都知道，一个channel绑定一个单线程执行器。终于在这里，我们看到channel绑定了单线程执行器
        //接着channel，不管是客户端还是服务端的，会把自己注册到绑定的单线程执行器中的selector上
        AbstractChannel.this.eventLoop = eventLoop;
        //又看到这个方法了，又一次说明在netty中，channel注册，绑定，连接等等都是异步的，由单线程执行器来执行
        if (eventLoop.inEventLoop(Thread.currentThread())) {
            register0(promise);
        } else {
            try {
                //如果调用该放的线程不是netty的线程，就封装成任务由线程执行器来执行
                eventLoop.execute(new Runnable() {
                    @Override
                    public void run() {
                        register0(promise);
                    }
                });
            } catch (Throwable t) {
                System.out.println(t.getMessage());
                //该方法先不做实现，等引入unsafe之后会实现
                //closeForcibly();
                closeFuture.setClosed();
                safeSetFailure(promise, t);
            }
        }
    }


    /**
     * @Author: PP-jessica
     * @Description:该方法并不在此类，而是在该类的静态内部类AbstractUnsafe中，现在先放在这里
     */
    private void register0(ChannelPromise promise) {
        try {
            if (!promise.setUncancellable() || !ensureOpen(promise)) {
                return;
            }
            //真正的注册方法
            doRegister();
            //修改注册状态
            registered = true;
            //把成功状态赋值给promise，这样它可以通知回调函数执行
            //我们在之前注册时候，把bind也放在了回调函数中
            safeSetSuccess(promise);
            //在这里给channel注册读事件
            beginRead();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public final void beginRead() {
        //如果是服务端的channel，这里仍然可能为false
        //那么真正注册读事件的时机，就成了绑定端口号成功之后
        if (!isActive()) {
            return;
        }
        try {
            doBeginRead();
        } catch (final Exception e) {
          throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public final void bind(final SocketAddress localAddress, final ChannelPromise promise) {
        try {
            doBind(localAddress);
            safeSetSuccess(promise);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected final void safeSetSuccess(ChannelPromise promise) {
        if (!promise.trySuccess()) {
            System.out.println("Failed to mark a promise as success because it is done already: "+promise);
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:下面三个方法都不在此处，而是在该类的静态内部类AbstractUnsafe中，现在先放在这里
     */
    protected void doRegister() throws Exception {}

    protected abstract void doBeginRead() throws Exception;

    protected abstract void doBind(SocketAddress localAddress) throws Exception;


    /**
     * @Author: PP-jessica
     * @Description:确保channel是打开的
     */
    protected final boolean ensureOpen(ChannelPromise promise) {
        if (isOpen()) {
            return true;
        }
        safeSetFailure(promise, newClosedChannelException(initialCloseCause));
        return false;
    }

    private ClosedChannelException newClosedChannelException(Throwable cause) {
        ClosedChannelException exception = new ClosedChannelException();
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }

    /**
     * @Author: PP-jessica
     * @Description:该方法其实也在unsafe类中
     */
    protected final void safeSetFailure(ChannelPromise promise, Throwable cause) {
        if (!promise.tryFailure(cause)) {
           throw new RuntimeException(cause);
        }
    }


    static final class CloseFuture extends DefaultChannelPromise {

        CloseFuture(AbstractChannel ch) {
            super(ch);
        }

        @Override
        public ChannelPromise setSuccess() {
            throw new IllegalStateException();
        }

        @Override
        public ChannelPromise setFailure(Throwable cause) {
            throw new IllegalStateException();
        }

        @Override
        public boolean trySuccess() {
            throw new IllegalStateException();
        }

        @Override
        public boolean tryFailure(Throwable cause) {
            throw new IllegalStateException();
        }

        boolean setClosed() {
            return super.trySuccess();
        }
    }
}
