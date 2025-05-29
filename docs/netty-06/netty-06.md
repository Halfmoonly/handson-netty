在正式开始讲解 Netty 的 Channel 体系之前，请大家稍微回顾一下之前我们手写 Netty 中的 NioEventLoop 类，下面之前的NioEventLoop代码。
```java
public class NioEventLoop extends SingleThreadEventLoop {
    ....

     //Java原生的服务端channel    
     private  ServerSocketChannel serverSocketChannel;
     //Java原生的客户端channel
     private  SocketChannel socketChannel;

    ....

    //该方法内的客户端和服务端channel耦合十分严重
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
                //注册客户端的channel到多路复用器，这里的操作是由服务器的单线程执行器执行的
                NioEventLoop nioEventLoop = (NioEventLoop) workerGroup.next().next();
                nioEventLoop.setServerSocketChannel(serverSocketChannel);
                //work线程自己注册的channel到执行器
                nioEventLoop.registerRead(socketChannel,nioEventLoop);
                logger.info("客户端连接成功:{}",socketChannel.toString());
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
```
上面的 NioEventLoop 中，要不停地判断触发当前 IO 事件的究竟是哪个 channel（服务端serversocketchannel或者客户端socketchannel）。显然，这种耦合的编码方式，让我们的程序臃肿不堪，显得我们程序员水平低下。

接下来是 SingleThreadEventLoop 中的代码，同样，也只贴一部分。
```java
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
                }
            });
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:该方法也要做重载
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
     * @Description:该方法也要做重载
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
```
大家可以看到，就因为不知道要注册到 selector 上的是客户端 channel 还是服务端 channel ，所以就要定义很多重载方法。

## 精简NioEventLoop和SingleThreadEventLoop的思路
其实看 NioEventLoop 这个类，实际上根本没必要把ServerSocketChannel 和 SocketChannel 定义成该类的成员变量。

记着Reactor体系：
- 一个 selector 可以注册多个 channel，因为服务端ServerSocketChannel会监听多个客户端SocketChannel。
- 一个 selector 只对应着一个单线程执行器 ，无论是服务端还是客户端单线程执行器启动的那一刻，就会在 run 方法中无限循环，在 run 方法内，要不停判断该单线程执行器持有的 selector 是否有 IO 事件到来，如果有就执行 IO 事件。

因为我们并不是在 selector 上有 IO 事件到来的时候才区分这些事件属于哪个 channel，而是在一开始将 channel 注册到 selector 时，就把 channel 隔离开了。

所以selector 根本没有必要知道是哪个 channel（ServerSocketChannel 或 SocketChannel） 的 IO 事件到来了

**所以，大家可以看到，上面给出的 NioEventLoop 其实根本没必要持有 ServerSocketChannel 和 SocketChannel。**

刚才我们分析了在 NioEventLoop 这个类中其实根本没必要定义 ServerSocketChannel 和 SocketChannel 为成员变量，但在 IO 事件到来的时候，比如说服务端要接收客户端连接了，这时候你总得使用 ServerSocketChannel 吧，或者是客户端 channel 要接收消息了，也总得使用 SocketChannel 来读取字节吧。

不知道大家对这两行代码还有印象吗？它们在我们的第一节课中就出现了。
```java
//把服务端channel以附件的形式存放到selectionKey上
SelectionKey selectionKey = serverSocketChannel.register(selector, 0, serverSocketChannel);
//通过selectionKey把服务端channel取出来
ServerSocketChannel attachment = (ServerSocketChannel)key.attachment();
```
这时候，我们就可以通过上面这种附件的方法，获得我们需要的客户端和服务端 channel。
- 在channel注册的时候，把自己作为附件传进去
- 这样日后，就可以通过key来获取attachment，返回的正好就是channel本身

所以SingleThreadEventLoop 的注册方法改动就是注册的时候把channel放到附件中。因为 SingleThreadEventLoop 类中都是重载方法，所以我们就列出了一个改动后的方法。
```java
private void register0(SocketChannel channel,NioEventLoop nioEventLoop) {
        try {
            channel.configureBlocking(false);
            //在这里把客户端channel放到附件中
            channel.register(nioEventLoop.unwrappedSelector(), SelectionKey.OP_CONNECT,channel);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }
```

现在，我就把客户端 channel 放到附件中了，这样在 NioEventLoop 中就可以获得客户端的 channel 了。请看下面的代码。
```java
private void processSelectedKey(SelectionKey k) throws Exception {
    //我们需要通过SelectionKey获得服务端的channel
     if (k.isAcceptable()){
         //如果是接收事件，那我们就知道现在的这个NioEventLoop是跟服务端channel绑定的
         //所以，我们直接获得服务端channel就行
       ServerSocketChannel channel = key.attachment()
        //省略处理逻辑
        ...
     }
    if (k.isReadable()) {
        //如果是读事件，就获得客户端channel
        SocketChannel channel = key.attachment()
    }
   
}
```
用上面这种方法，是不是就可以大大改观我们的代码了。但是大家也不要高兴得太早，NioEventLoop 类中的 channel 耦合问题虽然解决了，但是 SingleThreadEventLoop 类中的问题还没有解决。

这个类中之所以有很多重载方法，就是因为我们在注册 channel 到多路复用器时，要区分是客户端还是服务端的。那这个问题该怎么解决呢？结合前面几节课的知识，我们应该想到了，这种情况，只需引入一个接口就可解决。

比如，我们就引入一个 Channel 接口，让客户端和服务端的 channel 去实现这个接口。

## 引入Channel接口
既然引入了 Channel 接口，该接口的方法该怎么定义呢？考虑到服务端和客户端 channel 共有的一些方法，我们暂且先把 Channel 接口定义成这样。请看下面一段代码。
```java
public interface Channel {

     //该方法很重要，我们都知道，一个selector可以注册多个channel，但是一个channel只能对应
    //一个selector，一个selector对应着一个单线程执行器，所以一个channel就会对应一个单线程执行器
    //该方法就是用来得到该channel对应的单线程执行器
    EventLoop eventLoop();

    /**
     * @Author: PP-jessica
     * @Description:该方法并不在此接口，而是在ChannelOutboundInvoker接口，现在先放在这里
     */
    ChannelFuture close();

    /**
     * @Author: PP-jessica
     * @Description:该方法并不在此接口，而是在ChannelOutboundInvoker接口，现在先放在这里
     */
    void bind(SocketAddress localAddress, ChannelPromise promise);

    /**
     * @Author: PP-jessica
     * @Description:该方法并不在此接口，而是在ChannelOutboundInvoker接口，现在先放在这里
     */
    void connect(SocketAddress remoteAddress, final SocketAddress localAddress,ChannelPromise promise);
}
```
这几个方法大家应该都明白，接下来，我们就要改动一下 NioEventLoop 和 SingleThreadEventLoop 类中的关键代码了。

先看 SingleThreadEventLoop 的改动。方法签名改为接口声明Channel，同时在注册的时候直接把channel放到附件中，方便日后NioEventLoop通过key直接获取当前channel
```java
private void register(Channel channel,NioEventLoop nioEventLoop) {
        try {
            channel.configureBlocking(false);
            //在这里把客户端channel放到附件中
            channel.register(nioEventLoop.unwrappedSelector(), SelectionKey.OP_CONNECT,channel);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }
```

下面是 NioEventLoop 类中的变动。通过接口Channel直接接收serverSocketChannel或者socketChannel
```java
private void processSelectedKey(SelectionKey k) throws Exception {
    //我们需要通过SelectionKey获得服务端的channel
     if (k.isAcceptable()){
         //可以通过Channel接口来接收了
       Channel channel = key.attachment()
        //省略处理逻辑
        ...
     }
    if (k.isReadable()) {
       //可以通过Channel接口来接收了
        Channel channel = key.attachment()
    }
   
}
```

但是得到 Channel 接口之后呢？我们该通过 channel 读取数据或者是接收客户端连接了，可显然，Channel 接口中是没有这些方法的。那好办，添加到接口中就行了。

请大家先看下面这段代码中，在 IO 事件到来之后，要做出的相应行动。客户端和服务端 channel 的具体IO处理逻辑肯定是不一样的。
```java
                //服务端channel要处理的事件
            if (k.isAcceptable()) {
                SocketChannel socketChannel = serverSocketChannel.accept();
                socketChannel.configureBlocking(false);
                //注册客户端的channel到多路复用器，这里的操作是由服务器的单线程执行器执行的
                NioEventLoop nioEventLoop = (NioEventLoop) workerGroup.next().next();
                nioEventLoop.setServerSocketChannel(serverSocketChannel);
                //work线程自己注册的channel到执行器
                nioEventLoop.registerRead(socketChannel,nioEventLoop);
                logger.info("客户端连接成功:{}",socketChannel.toString());
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




                //客户端channel要处理的事件
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
```
**这样大段零散的代码出现在我们的类中，我们其实完全可以把这些代码封装成一个个方法，然后定义在 Channel 接口中，分别由客户端和服务端的 channel 去实现。**

但先别急着修改，既然要重构代码，我们就再仔细地思考一下代码，看都有哪些地方需要重构。比如有一个地方就很明显需要重构：
```java
private void register(Channel channel,NioEventLoop nioEventLoop) {
        try {
            channel.configureBlocking(false);
            //在这里把客户端channel放到附件中
            channel.register(nioEventLoop.unwrappedSelector(), SelectionKey.OP_CONNECT,channel);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }
```

在 SingleThreadEventLoop 类中，把 channel 注册到多路复用器时，是通过 channel 本身来调用 register 方法的，但是 Channel 接口中并没有该方法，所以也应该把该方法定义在接口中。

不过，让我们再进一步思考一下，不管是客户端还是服务端的 channel，都需要调用自身的 register 方法把自己注册到多路复用器上，所以无论是客户端还是服务端register方法内的逻辑都是相同的。

**既然是这种情况，我们就可以在 Channel 接口之下定义一个抽象的接口实现类，把客户端和服务端 channel 通用的方法都在抽象类中实现了，不通用的方法就定义成抽象方法，让不同的子类去实现**。

## 引入 AbstractChannel 抽象父类
既然我们的思路已经明确了，那就可以引入一个抽象父类，比如，我们就定义为 AbstractChannel，请看下面的两段代码。
```java
public interface Channel {

    //该方法很重要，我们都知道，一个selector可以注册多个channel，但是一个channel只能对应
    //一个selector，一个selector对应着一个单线程执行器，所以一个channel就会对应一个单线程执行器
    //该方法就是用来得到该channel对应的单线程执行器
    EventLoop eventLoop();

    /**
     * @Author: PP-jessica
     * @Description:该方法并不在此接口，而是在ChannelOutboundInvoker接口，现在先放在这里
     */
    ChannelFuture close();

    /**
     * @Author: PP-jessica
     * @Description:该方法并不在此接口，而是在ChannelOutboundInvoker接口，现在先放在这里
     */
    void bind(SocketAddress localAddress, ChannelPromise promise);

    /**
     * @Author: PP-jessica
     * @Description:该方法并不在此接口，而是在ChannelOutboundInvoker接口，现在先放在这里
     */
    void connect(SocketAddress remoteAddress, final SocketAddress localAddress,ChannelPromise promise);


    //新增加的方法
    public final void register(EventLoop eventLoop);

}
```
AbstractChannel.java
```java
public abstract class AbstractChannel implements Channel{
    /**
     * @Author: PP-jessica
     * @Description:每一个channel都要绑定到一个eventloop上
     */
    private volatile EventLoop eventLoop;

    //这个就是得到channel绑定的单线程执行器的方法
     @Override
    public EventLoop eventLoop() {
        EventLoop eventLoop = this.eventLoop;
        if (eventLoop == null) {
            throw new IllegalStateException("channel not registered to an event loop");
        }
        return eventLoop;
    }


     @Override
    public final void register(EventLoop eventLoop) {
        //在这里就把channel绑定的单线程执行器属性给赋值了
        AbstractChannel.this.eventLoop = eventLoop;
        //接下来就是之前写过的常规逻辑
        if (eventLoop.inEventLoop(Thread.currentThread())) {
            //为什么这里的register0方法可以不需要参数了？下面我们就会讲到
            register0();
        } else {
             //如果调用该放的线程不是netty的线程，就封装成任务由线程执行器来执行
                eventLoop.execute(new Runnable() {
                    @Override
                    public void run() {
                        //为什么这里的register0方法可以不需要参数了？
                        register0();
                    }
                });
        }
    }
}
```
SingleThreadEventLoop 类中的代码也需要做一点简单的变动。
```java
private void register(Channel channel,NioEventLoop nioEventLoop) {
        try {
            //这里只需要调用Channel接口中的方法即可，反正接口的实现类中已经把方法实现了
            channel.register(nioEventLoop);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
}

//上面的方法其实可以被重构为下面这样:
//register方法是被NioEventLoop调用的，而SingleThreadEventLoop是NioEventLoop的父类,从语法角度来说完全可以传this
//从NIO角度来说，一个channel要绑定一个唯一selector，正好是当前的的NioEventLoop
private void register(Channel channel) {
    try {
        //这里只需要调用Channel接口中的方法即可，反正接口的实现类中已经把方法实现了
        //只要被本身传递进去就可以，这里的this，就是调用register方法哪个NioEventoop
        channel.register(this);
    } catch (Exception e) {
        logger.error(e.getMessage());
    }
}
```

不过，一定会有人困惑，为什么 register0 方法没有参数了？

我们把 channel 注册到多路复用器上，最后执行的一定是 channel.register(nioEventLoop.unwrappedSelector(), SelectionKey.OP_CONNECT,channel) 这个 Java 原生的方法，

那我们要注册的 channel 和 selector 该怎么得到呢？这就要进一步完善我们引入的 Channel 的体系了。

## 引入 AbstractNioChannel 抽象类
我们现在是引入了 Channel 接口和 AbstractChannel 抽象类。

但在 Netty 中，AbstractChannel 抽象父类其实还要再继续细分。

我记得之前提到过，Netty 中不只有 NIO 模式的 channel，还有 Epoll 模式的 channel，所以，实际上在 AbstractChannel 抽象父类之下，还有一个抽象子类 AbstractNioChannel，

AbstractNioChannel 会和 AbstractEpollChannel 做区分，后者虽然也是 NIO 模式，但后者在特定的平台才能发挥作用。

所以，我们自然也应该在我们的手写项目中引入一个抽象的 AbstractNioChannel 类，在这个类的基础上，来制定抽象方法，让不同的子类去实现。

既然是这样，那我们可以继续分析一下，客户端和服务端的 channel 中，有哪些方法可以在抽象类 AbstractNioChannel 中定义成抽象的。

比如，之前我们在 NioEventLoop 中看到的那两段冗长的代码，就是接收到 IO 事件之后，处理 IO 事件的具体逻辑。我们当时说要把那些碎代码封装成一个个方法。现在我想好封装成什么方法了。所以，对 NioEventLoop 这个类的关键代码做了一些改动。
```java
private void processSelectedKey(SelectionKey k) throws Exception {
    //我们需要通过SelectionKey获得服务端的channel
     if (k.isAcceptable()){
         //仍然是通过多态赋值
       AbstractNioChannel channel = key.attachment()
        //具体的处理逻辑
        channel.read();
     }
    if (k.isReadable()) {
        AbstractNioChannel channel = key.attachment()
         //具体的处理逻辑
        channel.read();
    }
   
}
```
上面这段代码的逻辑同时适用于客户端和服务端：
- 客户端：只会走if (k.isReadable())取出对方回发的数据
- 服务端：接收到客户端连接成功的时候走if (k.isAcceptable())，之后客户端连接上了之后走if (k.isReadable())。并且走if (k.isAcceptable())的时候，还记得经典的NIO编程范式吗？服务端会在accept事件中回写客户端连接成功通知并注册客户端channel给到自己的selector并给客户端的channel设置可读事件

而且你看netty将服务端NioServerSocketChannel的isAcceptable逻辑和isReadable逻辑合并为一个方法read()了。 因此服务端的read()方法要比客户端的read()方法复杂的多

可见netty中客户端和服务端的read()方法逻辑根本大不相同，因此抽象父类 AbstractNioChannel 中定义抽象方法 read()，有必要让不同的子类去重写，

我们现在要做的，就是初步包装 Java 原生的 ServerSocketChannel 和 SocketChannel，让被包装过后的 channel 去实现各自的 read 方法。

既然客户端的 channel 主要是接收消息字节的，我们就把被包装过后的客户端 channel 称为 AbstractNioByteChannel，服务端是用来接收客户端连接的，就把它定义成 AbstractNioAcceptChannel。

本来这样就挺完美的了，但很遗憾，在 Netty 中服务端 channel 并不是这个名字，而是 AbstractNioMessageChannel 这个名字，那我们索性也就用这个名字。
- 客户端channel：AbstractNioByteChannel
- 服务端channel：AbstractNioMessageChannel

现在，让我们看看重构之后的程序，对抽象父类 AbstractChannel 稍作改动。
```java
public abstract class AbstractChannel implements Channel{

     /**
     * @Author: PP-jessica
     * @Description:当创建的是客户端channel时，parent为serversocketchannel
     * 如果创建的为服务端channel，parent则为null
     */
    private final Channel parent;

    
    /**
     * @Author: PP-jessica
     * @Description:每一个channel都要绑定到一个eventloop上
     */
    private volatile EventLoop eventLoop;

    //这个就是得到channel绑定的单线程执行器的方法
     @Override
    public EventLoop eventLoop() {
        EventLoop eventLoop = this.eventLoop;
        if (eventLoop == null) {
            throw new IllegalStateException("channel not registered to an event loop");
        }
        return eventLoop;
    }


     @Override
    public final void register(EventLoop eventLoop) {
        //在这里就把channel绑定的单线程执行器属性给赋值了
        AbstractChannel.this.eventLoop = eventLoop;
        //接下来就是之前写过的常规逻辑
        if (eventLoop.inEventLoop(Thread.currentThread())) {
            register0();
        } else {
             //如果调用该放的线程不是netty的线程，就封装成任务由线程执行器来执行
                eventLoop.execute(new Runnable() {
                    @Override
                    public void run() {
                        //为什么这里的register0方法可以不需要参数了？下面我们就会讲到
                        register0();
                    }
                });
        }
    }

    private void register0() {
            //真正的注册方法
            doRegister();
            //在这里给channel注册感兴趣事件
            beginRead();
    }

    //给channel注册感兴趣事件
    public final void beginRead() {
        doBeginRead();
    }

    

    //在很多框架中，有一个规定，那就是真正干事的方法都是do开头的
    protected void doRegister() throws Exception;

    protected abstract void doBeginRead() throws Exception;
}
```
定义一个抽象类 AbstractNioChannel，负责处理 NIO 模式下的数据。
```java
public abstract class AbstractNioChannel extends AbstractChannel {

    //该抽象类是serversocketchannel和socketchannel的公共父类
    private final SelectableChannel ch;

    //channel要关注的事件
    protected final int readInterestOp;

    //channel注册到selector后返回的key
    volatile SelectionKey selectionKey;

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

    //返回java原生channel的方法
    protected SelectableChannel javaChannel() {
        return ch;
    }

    //得到channel绑定的单线程执行器，NioEventLoop就是一个单线程执行器，这个已经讲过了
    @Override
    public NioEventLoop eventLoop() {
        return (NioEventLoop) super.eventLoop();
    }

    protected SelectionKey selectionKey() {
        assert selectionKey != null;
        return selectionKey;
    }

     //这里就为大家解决了为什么之前那个register0方法没有参数
    //在下面这个方法中，javaChannel返回的是java的原生channel
    //一个channel绑定一个单线程执行器，也就是NioEventLoop，而NioEventLoop恰好可以得到
    //其内部的selector。调用父类的eventLoop()方法，就可以得到该channel绑定的NioEventLoop
    @Override
    protected void doRegister() throws Exception {
        //在这里把channel注册到单线程执行器中的selector上，注意这里的第三个参数this，这意味着channel注册的时候，把自身，也就是nio类的channel
        //当作附件放到key上了，之后会用到这个
        selectionKey = javaChannel().register(eventLoop().unwrappedSelector(), 0, this);
    }

    //给channel设置感兴趣的事件
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
            //设置channel关注的事件，这里仍然是位运算做加减法
            selectionKey.interestOps(interestOps | readInterestOp);
        }
    }

   //抽象的read方法设置在这里
    protected abstract void read();
}
```
下面是被包装过后的服务端 AbstractNioMessageChannel，重写了父类AbstractNioChannel的read()方法和doReadMessages方法
```java
public abstract class AbstractNioMessageChannel extends AbstractNioChannel {

   //存放接收到的客户端连接的list
    private final List<Object> readBuf = new ArrayList<Object>();

    protected AbstractNioMessageChannel(Channel parent, SelectableChannel ch, int readInterestOp) {
        super(parent, ch, readInterestOp);
    }

    //实现的read方法，在该方法内接收客户端的连接
    @Override
    public void read() {
        do {
            //接收客户端的连接，存放在集合中
            int localRead = doReadMessages(readBuf);
            //返回值为0表示没有连接，直接退出即可
            if (localRead == 0) {
                break;
            }
            while (true);
        }
             int size = readBuf.size();
            for (int i = 0; i < size; i ++) {
                //把每一个客户端的channel注册到工作线程上，这里得不到workgroup
                //所以我们不在这里实现了，打印一下即可
                Channel child = (Channel) readBuf.get(i);
                System.out.println(child+"收到客户端的channel了");
                //TODO
            }
            //清除集合
            readBuf.clear();
        }
     //真正接收客户端连接的方法，留给子类去实现
    protected abstract int doReadMessages(List<Object> buf) throws Exception;
}
```
接下来是被包装过后的客户端 AbstractNioByteChannel。
```java
public abstract class AbstractNioByteChannel extends AbstractNioChannel{

    protected AbstractNioByteChannel(Channel parent, SelectableChannel ch) {
        super(parent, ch, SelectionKey.OP_READ);
    }

    //实现的read方法
    @Override
    public final void read() {
        //暂时用最原始的方法处理
        ByteBuffer byteBuf = ByteBuffer.allocate(1024);
        try {
            doReadBytes(byteBuf);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //真正读取消息的方法，留给子类去实现
    protected abstract int doReadBytes(ByteBuffer buf) throws Exception;
}
```
## 最终的客户端服务端实现类
首先是服务端的 NioServerSocketChannel。
```java
/**
 * @Author: PP-jessica
 * @Description:对serversocketchannel做了一层包装，同时也因为channel接口和抽象类的引入，终于可以使NioEventLoop和channel解耦了
 */
public class NioServerSocketChannel extends AbstractNioMessageChannel {
    //在无参构造器被调用的时候，该成员变量就被创建了
    private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();


    private static ServerSocketChannel newSocket(SelectorProvider provider) {
        try {
            return provider.openServerSocketChannel();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open a server socket.", e);
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:无参构造，当调用该构造器的时候，会调用到静态方法newSocket，返回一个ServerSocketChannel
     */
    public NioServerSocketChannel() {
        this(newSocket(DEFAULT_SELECTOR_PROVIDER));
    }

    public NioServerSocketChannel(ServerSocketChannel channel) {
        //创建的为NioServerSocketChannel时，没有父类channel，SelectionKey.OP_ACCEPT是服务端channel的关注事件
        super(null, channel, SelectionKey.OP_ACCEPT);
    }

    /**
     * @Author: PP-jessica
     * @Description:该方法是服务端channel接收连接的方法
     */
    @Override
    protected int doReadMessages(List<Object> buf) throws Exception {
        //有连接进来，创建出java原生的客户端channel
        SocketChannel ch = SocketUtils.accept(javaChannel());
        try {
            if (ch != null) {
                //创建niosocketchannel
                buf.add(new NioSocketChannel(this, ch));
                return 1;
            }
        } catch (Throwable t) {
           t.printStackTrace();
            try {
                //有异常则关闭客户端的channel
                ch.close();
            } catch (Throwable t2) {
                throw new RuntimeException("Failed to close a socket.", t2);
            }
        }
        return 0;
    }
}
```
接下来是客户端的 NioSocketChannel。
```java
/**
 * @Author: PP-jessica
 * @Description:对socketchannel做了一层包装，同时也因为channel接口和抽象类的引入，终于可以使NioEventLoop和channel解耦了
 */
public class NioSocketChannel extends AbstractNioByteChannel {
    
    private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();


    private static SocketChannel newSocket(SelectorProvider provider) {
        try {
            return provider.openSocketChannel();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open a socket.", e);
        }
    }


    /**
     * @Author: PP-jessica
     * @Description:无参构造器，netty客户端初始化的时候，channel工厂反射调用的就是这个构造器
     */
    public NioSocketChannel() {
        this(DEFAULT_SELECTOR_PROVIDER);
    }

    public NioSocketChannel(SelectorProvider provider) {
        this(newSocket(provider));
    }

    public NioSocketChannel(SocketChannel socket) {
        this(null, socket);
    }

    public NioSocketChannel(Channel parent, SocketChannel socket) {
        super(parent, socket);
    }

    //读取客户端消息的方法
     @Override
    protected int doReadBytes(ByteBuffer byteBuf) throws Exception {
        int len = javaChannel().read(byteBuf);
        byte[] buffer = new byte[len];
        byteBuf.flip();
        byteBuf.get(buffer);
        System.out.println("客户端收到消息:{}"+new String(buffer));
        //返回读取到的字节长度
        return len;
    }
}
```
到这里，我们对 Channel 体系的完善和程序的重构也总算是完成了，下面，就让我们以服务端的启动作为例子，以这个例子看一看被包装过后的服务端 channel 在服务端启动过程中做了什么事。

在创建测试类之前，我们还需要对另一处地方做一点改动，就是我们的服务端启动类 ServerBootstrap。代码如下：
```java
public class ServerBootstrap<C extends Channel> {
    /**
     * @Author: PP-jessica
     * @Description:创建channel的反射工厂，从此之后，不必再让用户自己创建channel对象了，而是由反射工厂为我们创建
     */
    public ServerBootstrap channel(Class<? extends C> channelClass) {
        this.channelFactory = new ReflectiveChannelFactory<C>(channelClass);
        return this;
    }

    private ChannelFuture doBind(SocketAddress localAddress) {
        //服务端的channel在这里初始化，然后注册到单线程执行器的selector上
        final ChannelFuture regFuture = initAndRegister();
        //后面其他流程就省略了
        ......
    }

    final ChannelFuture initAndRegister() {
        Channel channel = null;
        //在这里初始化服务端channel，反射创建对象调用的无参构造器
        //可以去NioServerSocketChannel类中看看无参构造器中做了什么
        channel = channelFactory.newChannel();
        //这里是异步注册的，一般来说，bossGroup设置的都是一个线程
        ChannelFuture regFuture = bossGroup.next().register(channel);
        return regFuture;
    }
}
```

## 梳理程序执行流程
ReflectiveChannelFactory 和 ChannelFuture 的具体实现我就不再讲解了。现在，我们的关注重点就是眼下的这个 NioServerSocketChannel。下面就是我们创建的测试类：
```java
public class ServerTest {
    public static void main(String[] args) throws IOException, InterruptedException {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(2);
       ChannelFuture channelFuture = serverBootstrap.
                group(bossGroup,workerGroup).
                channel(NioServerSocketChannel.class).
                bind(8080).addListener(future -> System.out.println("我绑定成功了"));
    }
}
```
当我们启动这个测试类，程序执行到第 8 行的时候，就会通过 ServerBootstrap 中的 channel 方法，把要创建的 channel 类型传递到反射工厂中，这样反射工厂就知道要为用户创建什么类型的 channel 了。就像下面这样。
```java
public ServerBootstrap channel(Class<? extends C> channelClass) {
        this.channelFactory = new ReflectiveChannelFactory<C>(channelClass);
        return this;
    }
```
```java
public class ReflectiveChannelFactory<T extends Channel> implements ChannelFactory<T> {
    //类的构造器
    private final Constructor<? extends T> constructor;

    public ReflectiveChannelFactory(Class<? extends T> clazz) {
        //在这里构造器被赋值
        this.constructor = clazz.getConstructor();
    }

    //反射创建对象的方法
    public T newChannel() {
       return constructor.newInstance(); 
    }

    //其他的省略
    ......
}
```
接着，程序就会来到 bind 方法内部，bind 方法又会一路调用到 doBind 方法内部，进而开始执行 initAndRegister 方法。这个方法从名字上就可以看出其功能，就是用来初始化 channel 并且将其注册到多路复用器上的。具体的逻辑如下。
```java
 final ChannelFuture initAndRegister() {
        Channel channel = null;
        //在这里初始化服务端channel，反射创建对象调用的无参构造器
        //可以去NioServerSocketChannel类中看看无参构造器中做了什么
        channel = channelFactory.newChannel();
        //这里是异步注册的，一般来说，bossGroup设置的都是一个线程。
        ChannelFuture regFuture = bossGroup.next().register(channel);
        return regFuture;
    }
```
在这个方法内，调用反射工厂的 newChannel 方法，创建了一个被包装过后的 NioServerSocketChannel，这个 channel 就是为服务端服务的。

既然是反射创建了 channel，并且调用的是无参构造器，那我们就要看看在 NioServerSocketChannel 的无参构造器中有什么逻辑。所以，这时候我们的程序就会来到 NioServerSocketChannel 的无参构造器中。
```java
    //在无参构造器被调用的时候，该成员变量就被创建了
    private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();


    private static ServerSocketChannel newSocket(SelectorProvider provider) {
        try {
            return provider.openServerSocketChannel();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open a server socket.", e);
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:无参构造，当调用该构造器的时候，会调用到静态方法newSocket，返回一个ServerSocketChannel
     */
    public NioServerSocketChannel() {
        this(newSocket(DEFAULT_SELECTOR_PROVIDER));
    }

    public NioServerSocketChannel(ServerSocketChannel channel) {
        //创建的为NioServerSocketChannel时，没有父类channel，SelectionKey.OP_ACCEPT是服务端channel的关注事件
        super(null, channel, SelectionKey.OP_ACCEPT);
    }
```
在无参构造器方法被调用的过程中，该类的静态方法 newSocket 会帮用户创建一个 Java 原生的 ServerSocketChannel，并且和 SelectionKey.OP_ACCEPT 一起传入父类的构造器中。

父类 AbstractNioMessageChannel 的构造器的逻辑就很简单，只是继续向父类 AbstractNioChannel 构造器传递参数而已，到了 AbstractNioChannel 类中，我们可以看到几个关键属性被赋值了。
```java
 //该抽象类是serversocketchannel和socketchannel的公共父类
    private final SelectableChannel ch;

    //channel要关注的事件
    protected final int readInterestOp;

        protected AbstractNioChannel(Channel parent, SelectableChannel ch, int readInterestOp) {
        super(parent);
         //这里把java的原生channel赋值给该成员变量了
        this.ch = ch;
         //服务端channel感兴趣的时间也被赋值了
        this.readInterestOp = readInterestOp;
        //设置服务端channel为非阻塞模式
        ch.configureBlocking(false);
        }
```

到这里，initAndRegister 方法中的 channelFactory.newChannel() 这行代码的逻辑才算是走完了。

接着就该执行 bossGroup.next().register(channel) 这行代码。bossGroup.next() 的逻辑就不再赘述了，之前都讲过了，我们直接看重构之后的 register(channel) 的逻辑。

当执行 register(channel) 这行代码的时候，这是一个 NioEventLoop 在调用它自己的 register 方法，所以代码的最终逻辑就会来到 SingleThreadEventLoop 类中的 register 方法内。
```java
private void register(Channel channel) {
        try {
            //这里只需要调用Channel接口中的方法即可，反正接口的实现类中已经把方法实现了
            //只要被本身传递进去就可以，这里的this，就是调用register方法哪个NioEventoop
            channel.register(this);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }
```
到了这里，我们的代码终于执行到 channel.register(this) 这行逻辑中了。这时候大家可别忘了，虽然 register 方法的形参是一个 Channel 接口，但我们的反射工厂创建的是 NioServerSocketChannel，所以该 channel 的所有父类中的方法，它都可以调用。

因此，我们就来到了 AbstractChannel 抽象父类的 register 方法内，真正执行注册的是 register0 方法。

但是在该类的 register0 方法内，是两个需要被重写的模版方法，doRegister 和 beginRead。
```java
 private void register0() {
            //真正的注册方法
            doRegister();
            //在这里给channel注册读事件
            beginRead();
    }
```

先将 channel 注册到多路复用器上，然后设置 channel 感兴趣的事件。所以，我们的代码逻辑就会来到 AbstractNioChannel 中，在这个抽象类中，关键的属性都在它的构造函数中被赋值了
- 所以我们 Java 原生的 channel 也可以得到了，用来执行真正的注册多路复用器方法。
- 而 readInterestOp 也有值了，就可以在 doBeginRead 中给 channel 设置感兴趣的事件。

```java
public abstract class AbstractNioChannel extends AbstractChannel {

    //该抽象类是serversocketchannel和socketchannel的公共父类
    private final SelectableChannel ch;

    //channel要关注的事件
    protected final int readInterestOp;

    //channel注册到selector后返回的key
    volatile SelectionKey selectionKey;

    protected AbstractNioChannel(Channel parent, SelectableChannel ch, int readInterestOp) {
        super(parent);
        this.ch = ch;
        this.readInterestOp = readInterestOp;
        //设置服务端channel为非阻塞模式
        ch.configureBlocking(false);
        ......
      
    }

    //返回java原生channel的方法
    protected SelectableChannel javaChannel() {
        return ch;
    }

    //得到channel绑定的单线程执行器，NioEventLoop就是一个单线程执行器，这个已经讲过了
    @Override
    public NioEventLoop eventLoop() {
        return (NioEventLoop) super.eventLoop();
    }

    protected SelectionKey selectionKey() {
        assert selectionKey != null;
        return selectionKey;
    }

     //这里就为大家解决了为什么之前那个register0方法没有参数
    //在下面这个方法中，javaChannel返回的是java的原生channel
    //一个channel绑定一个单线程执行器，也就是NioEventLoop，而NioEventLoop恰好可以得到
    //其内部的selector。调用父类的eventLoop()方法，就可以得到该channel绑定的NioEventLoop
    @Override
    protected void doRegister() throws Exception {
        //在这里把channel注册到单线程执行器中的selector上,注意这里的第三个参数this，这意味着channel注册的时候把本身，也就是nio类的channel
        //当作附件放到key上了，之后会用到这个。
        selectionKey = javaChannel().register(eventLoop().unwrappedSelector(), 0, this);
    }

    //给channel设置感兴趣的事件
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
            //设置channel关注的事件，这里仍然是位运算做加减法
            selectionKey.interestOps(interestOps | readInterestOp);
        }
    }

   //抽象的read方法设置在这里
    protected abstract void read();
}
```

到此为止，initAndRegister 方法的关键逻辑就全分析完了，与客户端解耦的服务端 channel 具体是怎样工作的，我们也已经清楚了。如果继续往下分析，就可以分析当服务端 channel 有连接事件到来了，它的调用流程又是怎样的。不管是怎样，最后都会走到 NioServerSocketChannel 中执行真正的 doReadMessages 方法来接收客户端的连接。

最后，为大家补上一张简图，梳理一下我们引入的这些 channel 的继承关系。

![channel接口体系.png](channel%E6%8E%A5%E5%8F%A3%E4%BD%93%E7%B3%BB.png)
