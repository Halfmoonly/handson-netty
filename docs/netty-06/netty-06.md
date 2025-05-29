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


