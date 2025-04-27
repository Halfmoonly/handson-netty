NIO 为什么诞生？

最开始，在进行网络 IO 的过程中，是以 BIO（阻塞 IO 模型），也就是以流的形式，一次一个字节地接收或发送数据。比如服务端接收客户端的连接时，服务端每接收到一个连接就要创建一个线程，并且该线程只对该连接负责。如果该线程管理的客户端连接迟迟没有再发送数据过来，那么该线程就会一直阻塞，直到有数据到来，才会继续开始工作。

比如，当服务端要接收来自 4 个客户端的连接时，就要创建4个线程，如果之后这些连接再没发送数据过来，这 4 个线程就会阻塞。连接客户端较少的情况下并不会有处理压力，只是有些浪费线程资源，但如果来自客户端的连接不断增加，达到成千上万个，再按照 BIO 模式接收网络数据就太荒谬了。

因为线程并不是凭空存在的，每一个线程都会占用一定的内存，而且线程的切换会耗费大量时间，甚至切换线程的时间会超过我们程序执行的时间。另外在 Java 中，线程的创建和销毁实际上都是操作系统帮我们完成的，频繁创建和销毁线程也很耗时。最后，也是最不能容忍的，如果线程管理的客户端连接没有数据到来，那么大面积的线程都将阻塞，资源会被白白浪费了。

由此可见，在处理器核数一定的情况下，BIO 实际上只适用于客户端连接比较少的情况，并不适用于高并发场景。那么，在处理器核数一定的情况下，如何创建较少的线程来管理众多客户端连接呢？这就轮到 NIO的多路复用机制登场了。

## NIO 中的多路复用机制
和 BIO 有所区别，NIO 并不是以流的方式来接收和发送数据的，它用的是 Buffer 缓冲区。也就是说，在 NIO 中，要操作的数据全部先放入缓冲区，再由缓冲区向外发送，这显然比一次一个字节处理数据的流要高效很多。

那么，NIO 的 IO 多路复用机制又是什么，如何利用它实现以较少的线程管理众多客户端连接呢？

所谓多路复用，说到底就一句话，就是用一个线程管理多个连接，管理多个连接的数据收发 。当然， 多路复用 也可以理解为 是用 selector 管理多个 channel ，处理每个 channel 到来的 IO 事件。 这两种解释其实只是外在和内在的区别，因为从表面上看，确实是一个线程管理了多个客户端连接，但深入到内部，实际上是 selector 管理了多个客户端 channel，每当有 IO 事件到来，就交给该线程去处理。

![nio.png](nio.png)

多路复用最终的效果就是，服务器再也不必每接收一个客户端连接就创建一个线程了。

当然，IO 多路复用并不是 NIO 独有的，该机制是在操作系统中实现的，NIO 只是使用了该机制，在处理数据时发挥出了强大的效果。下面，我们就从代码中看看 IO 多路复用的好处是怎么体现出来的。

## NIO编程范式
下面是 NIO 中最基础的两段代码，从中可以看出服务端和客户端的工作原理。说起来确实很简单，不管是服务端还是客户端，都要创建一个 selector，然后将客户端 channel 和服务端的 channel 注册到各自的 sleector 上，并设置感兴趣的事件。当有事件到来的时候，就可以从 selector 中取出，再作出相应的处理即可。

客户端程序SimpleClient
```java

public class SimpleClient {
    public static void main(String[] args) throws IOException, InterruptedException {
        Logger logger = LoggerFactory.getLogger(SimpleClient.class);

        //得到客户端的channel
        SocketChannel socketChannel = SocketChannel.open();
        //设置非阻塞
        socketChannel.configureBlocking(false);
        //得到selector
        Selector selector = Selector.open();
        //把客户端的channel注册到selector上
        SelectionKey selectionKey = socketChannel.register(selector, 0);
        //设置事件
        selectionKey.interestOps(SelectionKey.OP_CONNECT);
        //客户端的channel去连接服务器
        socketChannel.connect(new InetSocketAddress(8080));
        //开始轮询事件
        while (true) {
            //无事件则阻塞
            selector.select();
            //得到事件的key
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                //如果是连接成功事件
                if (key.isConnectable()) {
                    if (socketChannel.finishConnect()) {
                        socketChannel.register(selector,SelectionKey.OP_READ);
                        logger.info("已经注册了读事件！");
                        //紧接着向服务端发送一条消息
                        socketChannel.write(ByteBuffer.wrap("客户端发送成功了".getBytes()));
                    }
                }
                //如果是读事件
                if (key.isReadable()) {
                    SocketChannel channel = (SocketChannel)key.channel();
                    //分配字节缓冲区来接受服务端传过来的数据
                    ByteBuffer buffer = ByteBuffer.allocate(1024);
                    //向buffer写入客户端传来的数据
                    int len = channel.read(buffer);
                    byte[] readByte = new byte[len];
                    buffer.flip();
                    buffer.get(readByte);
                    logger.info("读到来自服务端的数据：" + new String(readByte));
                }
            }
        }
    }
}
```

服务端程序SimpleServer
```java
public class SimpleServer {

    public static void main(String[] args) throws IOException {
        Logger logger = LoggerFactory.getLogger(SimpleServer.class);

        //创建服务端channel
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        //设置channel非阻塞
        serverSocketChannel.configureBlocking(false);
        //获得selector
        Selector selector = Selector.open();
        //把channel注册到selector上,现在还没有给key设置感兴趣的事件
        SelectionKey selectionKey = serverSocketChannel.register(selector, 0, serverSocketChannel);
        //给key设置感兴趣的事件
        selectionKey.interestOps(SelectionKey.OP_ACCEPT);
        //绑定端口号
        serverSocketChannel.bind(new InetSocketAddress(8080));
        //然后开始接受连接,处理事件,整个处理都在一个死循环之中
        while (true) {
            //当没有事件到来的时候，这里是阻塞的,有事件的时候会自动运行
            selector.select();
            //如果有事件到来，这里可以得到注册到该selector上的所有的key，每一个key上都有一个channel
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            //得到集合的迭代器
            Iterator<SelectionKey> keyIterator = selectionKeys.iterator();
            while (keyIterator.hasNext()) {
                //得到每一个key
                SelectionKey key = keyIterator.next();
                //首先要从集合中把key删除，否则会一直报告该key
                keyIterator.remove();
                //接下来就要处理事件，判断selector轮询到的是什么事件，并根据事件作出回应
                //如果是连接事件
                if (key.isAcceptable()) {
                    //得到服务端的channel,这里有两种方式获得服务端的channel，一种是直接获得,一种是通过attachment获得
                    //因为之前把服务端channel注册到selector上时，同时把serverSocketChannel放进去了
                    ServerSocketChannel channel = (ServerSocketChannel)key.channel();
                    //ServerSocketChannel attachment = (ServerSocketChannel)key.attachment();
                    //得到客户端的channel
                    SocketChannel socketChannel = channel.accept();
                    socketChannel.configureBlocking(false);
                    //接下来就要管理客户端的channel了，和服务端的channel的做法相同，客户端的channel也应该被注册到selector上
                    //通过一次次的轮询来接受并处理channel上的相关事件
                    //把客户端的channel注册到之前已经创建好的selector上
                    SelectionKey socketChannelKey = socketChannel.register(selector, 0, socketChannel);
                    //给客户端的channel设置可读事件
                    socketChannelKey.interestOps(SelectionKey.OP_READ);
                    logger.info("客户端连接成功！");
                    //连接成功之后，用客户端的channel写回一条消息
                    socketChannel.write(ByteBuffer.wrap("我发送成功了".getBytes()));
                    logger.info("向客户端发送数据成功！");
                }
                //如果接受到的为可读事件，说明要用客户端的channel来处理
                if (key.isReadable()) {
                    //同样有两种方式得到客户端的channel，这里只列出一种
                    SocketChannel channel = (SocketChannel)key.channel();
                    //分配字节缓冲区来接受客户端传过来的数据
                    ByteBuffer buffer = ByteBuffer.allocate(1024);
                        //向buffer写入客户端传来的数据
                        int len = channel.read(buffer);
                        logger.info("读到的字节数：" + len);
                        if (len == -1) {
                            channel.close();
                            break;
                        }else{
                        //切换buffer的读模式
                        buffer.flip();
                        logger.info(Charset.defaultCharset().decode(buffer).toString());
                    }
                }
            }
        }
    }
}
```
在我们上面给出的 SimpleServer 代码中，服务端接收连接的方式已经是多路复用了

服务端只有一个 main 函数的线程在工作，该线程持有一个 selector，接收到的客户端连接全都注册到该 selector上。如果有 IO 事件到来，也是该线程处理。

## 测试
先启动服务端，后启动客户端即可

## 遗留问题
看起来我们的服务端代码已经使用了 NIO 的多路复用，在性能上比 BIO 要好很多了。但情况真的是这样吗？

如果服务器的处理器是单核的，在这样的服务器中单线程的执行效率一定高于多线程，因为线程切换会浪费时间。

但是，在多核服务器中，将并发的线程数量动态调整在某个范围内，和单线程执行任务比起来，其工作效率一定是遥遥领先的。

所以服务端只创建一个线程完全发挥不出多核处理器的优势，我们完全可以创建更多的线程，让更多线程来工作，管理更多的客户端连接。多个线程同时工作，服务端的工作效率不就大大提高了

但是，我们要怎么增加线程的数量呢？难道要像 BIO 那样，收到一个客户端连接就创建一个线程？

别忘了，我们刚刚学习了 IO 多路复用，可以直接在 NIO 的基础上增加线程数量，多搞几个可以多路复用的线程。比如服务端一下子涌入 50 个客户端连接，那我们就搞两个多路复用的线程，每个线程处理 25 个连接。当连接没有事件的时候，线程也会阻塞，但阻塞也仅仅是阻塞两个线程。如果我们服务器的 CPU 是双核的，我们这么做的效率绝对会比在服务端创建 50 个 BIO 线程高很多，我们甚至可以搞 4 个多路复用线程。