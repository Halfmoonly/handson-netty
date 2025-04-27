看看我们眼前这一大坨代码，你不觉得很丑陋吗？明明是一个 Executor，却杂糅了太多不相关的方法。Java 的线程池都有什么方法，你能立刻回答出来吗？也许你不能，但你肯定知道，Java 的线程池至少没有什么 register、select、processSelectedKey 等各种各样的方法。那我们搞一个单线程执行器，却写了这么多十分必要但是又与执行器无关的方法，这样的代码展示出去真就不怕别人笑话啊。所以，让我们对 SingleThreadEventExecutor 进行重构，把不相关的属性和方法剔除。这样的话，selector、provider 这两个属性肯定不能放在这里了，register、register0、openSecector、select、processSelectedKeys、processSelectedKey 这几个方法肯定也要另寻他类，至于同名的 run 方法，我们暂时搁置到一边。

**本节我们来解耦单线程执行器**

但是从该类剔除的那些属性和方法要怎么安排呢？空有一个执行器，没有活干也不行啊。

## 引入 NioEventLoop 类

考虑到我们新创建的线程采用的是 NIO 模式，并且是在一个循环中处理各种 IO 事件，所以，我决定先搞一个名叫 NioEventLoop 的类，并且希望该类拥有的方法只处理 IO 事件。这样一来，我们的 openSecector、select、processSelectedKeys、processSelectedKey、selector 和 provider 似乎都有了归宿。但是 register、register0 这两个方法既不处理 IO 事件，也不属于线程池，又该放在哪里？这个一会再讨论，我们先思考下 NioEventLoop 类和 SingleThreadEventExecutor 类的关系。如果是组合，该谁组合谁？如果是继承，该谁继承谁？

我们把目光放在同名的 run 方法上。

```java
 public void run() {
        while (true) {
            try {
                //没有事件就阻塞在这里
                select();
                //如果走到这里，就说明selector上有io事件了,就处理就绪事件
                processSelectedKeys(selector.selectedKeys());
            } catch (Exception e) {
                e.printStackTrace();
            }finally {
                //执行单线程执行器中的所有任务
                runAllTasks();
            }
        }
    }

```

在该方法中，调用了属于 NioEventLoop 的 select 和 processSelectedKeys 方法，同时又调用了单线程执行器的runAllTasks 方法。如果我把 run 方法放在 NioEventLoop 中，就意味着 SingleThreadEventExecutor 要成为NioEventLoop 的成员变量，因为 runAllTasks() 是要由单线程执行器来调用的。可是单线程执行器创建线程的时候，又必须要调用该 run 方法，这意味着 NioEventLoop 又必须成为 SingleThreadEventExecutor 的成员变量。

这样一来，代码就会变得既复杂又丑陋。有同学可能会提议让 run 方法成为静态方法，但这样一来 run 方法内的方法都要变成静态的，岂不是更麻烦。同理，如果把 run 方法仍然放在 SingleThreadEventExecutor 中，这意味着NioEventLoop 要成为单线程执行器的成员变量，可惜的是，在 NioEventLoop 中的 select 方法内，需要调用单线程执行器的 hasTasks 方法，于是 NioEventLoop 似乎又要持有单线程执行器为成员变量。总之，使用组合来维系这两个类的关系，总会出现矛盾。

其实我们早就该想到了，这两个类的一些方法耦合得很厉害。既然如此，我们正好可以使用继承来明确两个类的职责。父类的方法子类可以直接使用，子类可以在父类的基础上扩展自己的方法。这样一来，类的关系就很明朗了。**NioEventLoop 应该作为 SingleThreadEventExecutor 的子类，并且在父类方法的基础上，将处理 IO 事件的方法定义在自己内部。**

这里我想再多聊几句。**在我们的编程习惯中，既然在类中使用了继承关系，肯定是奔着在子类中扩展更多方法去的。难道你见过有比父类功能还少的子类？SingleThreadEventExecutor 本来就是被施加了限制的类，我们不可能让它继承 NioEventLoop，否则，NioEventLoop 作为父类仍然无法调用单线程执行器的方法。**

可是同名 run 方法既要在父类中出现，又用到了子类的方法。这很简单，我们把它定义为一个父类的抽象方法，让子类 NioEventLoop 去实现。如此一来，单线程执行器就变成了一个抽象类，而我们最终创建的是子类 NioEventLoop，两全其美了。

等等，那 register、register0 这两个方法怎么办？既然这两个方法既不处理 IO 事件，也不属于线程池，那我再搞一个新的类，让 register、register0 这两个方法定义在该类中 **。比如我就搞一个 SingleThreadEventLoop，SingleThreadEventExecutor 和 NioEventLoop 类的名字各取一部分，让它定义 register** **、** **register0 方法，并且让它充当 SingleThreadEventExecutor 的子类，NioEventLoop 的父类。这样，NioEventLoop 既不用把不相关的方法定义在自己内部，也可以使用。**

## **重构单线程执行器**

以下是重构好的代码，我们先来看抽象父类。

```java
public abstract class  SingleThreadEventExecutor implements Executor {

    private static final Logger logger = LoggerFactory.getLogger(SingleThreadEventExecutor.class);

    //任务队列的容量，默认是Integer的最大值
    protected static final int DEFAULT_MAX_PENDING_TASKS = Integer.MAX_VALUE;

    private final Queue<Runnable> taskQueue;

    private final RejectedExecutionHandler rejectedExecutionHandler;

    private volatile boolean start = false;

    private Thread thread;

    public SingleThreadEventExecutor() {
        this.taskQueue = newTaskQueue(DEFAULT_MAX_PENDING_TASKS);
        this.rejectedExecutionHandler = new ThreadPoolExecutor.AbortPolicy();
    }

    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
        return new LinkedBlockingQueue<Runnable>(maxPendingTasks);
    }

    @Override
    public void execute(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        //把任务提交到任务队列中
        addTask(task);
        //启动单线程执行器中的线程
        startThread();
    }

    private void addTask(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        //如果添加失败，执行拒绝策略
        if (!offerTask(task)) {
            reject(task);
        }
    }

    private void startThread() {
        if (start) {
            return;
        }
        start = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                //这里是得到了新创建的线程
                thread = Thread.currentThread();
                //执行run方法，在run方法中，就是对io事件的处理
                SingleThreadEventExecutor.this.run();
            }
        }).start();
        logger.info("新线程创建了！");
    }

    final boolean offerTask(Runnable task) {
        return taskQueue.offer(task);
    }

    /**
     * @Author: PP-jessica
     * @Description:判断任务队列中是否有任务
     */
    protected boolean hasTasks() {
        logger.info("我没任务了！");
        return !taskQueue.isEmpty();
    }
  
    /**
     * @Author: PP-jessica
     * @Description:执行任务队列中的所有任务
     */
    protected void runAllTasks() {
        runAllTasksFrom(taskQueue);
    }

    protected void runAllTasksFrom(Queue<Runnable> taskQueue) {
        //从任务对立中拉取任务,如果第一次拉取就为null，说明任务队列中没有任务，直接返回即可
        Runnable task = pollTaskFrom(taskQueue);
        if (task == null) {
            return;
        }
        for (;;) {
            //执行任务队列中的任务
            safeExecute(task);
            //执行完毕之后，拉取下一个任务，如果为null就直接返回
            task = pollTaskFrom(taskQueue);
            if (task == null) {
                return;
            }
        }
    }

    private void safeExecute(Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            logger.warn("A task raised an exception. Task: {}", task, t);
        }
    }

    protected static Runnable pollTaskFrom(Queue<Runnable> taskQueue) {
        return taskQueue.poll();
    }

    /**
     * @Author: PP-jessica
     * @Description:判断当前执行任务的线程是否是执行器的线程
     */
    public boolean inEventLoop(Thread thread) {
        return thread == this.thread;
    }

    protected final void reject(Runnable task) {
        //rejectedExecutionHandler.rejectedExecution(task, this);
    }

    protected abstract void run();
}

```

再来看抽象子类SingleThreadEventLoop

```java
public abstract class SingleThreadEventLoop extends SingleThreadEventExecutor{

    private static final Logger logger = LoggerFactory.getLogger(SingleThreadEventLoop.class);

    public SingleThreadEventLoop() {

    }

    public void register(SocketChannel socketChannel,NioEventLoop nioEventLoop) {
        //如果执行该方法的线程就是执行器中的线程，直接执行方法即可
        if (inEventLoop(Thread.currentThread())) {
            register0(socketChannel,nioEventLoop);
        }else {
            //在这里，第一次向单线程执行器中提交任务的时候，执行器终于开始执行了,新的线程也开始创建
            nioEventLoop.execute(new Runnable() {
                @Override
                public void run() {
                    register0(socketChannel,nioEventLoop);
                    logger.info("客户端的channel已注册到新线程的多路复用器上了！");
                }
            });
        }
    }

    private void register0(SocketChannel channel,NioEventLoop nioEventLoop) {
        try {
            channel.configureBlocking(false);
            channel.register(nioEventLoop.selector(), SelectionKey.OP_READ);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }
}

```

然后来看 NioEventLoop 的代码。

```java
public class NioEventLoop extends SingleThreadEventLoop{

    private static final Logger logger = LoggerFactory.getLogger(NioEventLoop.class);

    private final SelectorProvider provider;

    private Selector selector;

    public NioEventLoop() {
        //java中的方法，通过provider不仅可以得到selector，还可以得到ServerSocketChannel和SocketChannel
        this.provider = SelectorProvider.provider();
        this.selector = openSecector();
    }

    /**
     * @Author: PP-jessica
     * @Description:得到用于多路复用的selector
     */
    private Selector openSecector() {
        try {
            selector = provider.openSelector();
            return selector;
        } catch (IOException e) {
            throw new RuntimeException("failed to open a new selector", e);
        }
    }

    public Selector selector() {
        return selector;
    }
  
    private void select() throws IOException {
        Selector selector = this.selector;
        //这里是一个死循环
        for (;;){
            //如果没有就绪事件，就在这里阻塞3秒，有限时的阻塞
            logger.info("新线程阻塞在这里3秒吧。。。。。。。");
            int selectedKeys = selector.select(3000);
            //如果有io事件或者单线程执行器中有任务待执行，就退出循环
            if (selectedKeys != 0 || hasTasks()) {
                break;
            }
        }
    }

    private void processSelectedKeys(Set<SelectionKey> selectedKeys) throws IOException {
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

    private void processSelectedKey(SelectionKey k) throws IOException {
        //如果是读事件
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
            logger.info("新线程收到客户端发送的数据:{}",new String(bytes));
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                //没有事件就阻塞在这里
                select();
               //如果走到这里，就说明selector没有阻塞了，可能有IO事件，可能任务队列中有任务
                processSelectedKeys(selector.selectedKeys());
            } catch (Exception e) {
                e.printStackTrace();
            }finally {
                //执行单线程执行器中的所有任务
                runAllTasks();
            }
        }
    }
}

```

## 重构版本测试

最后是服务端的代码，客户端保持不变，和上节课的一样。

```java
public class TestServer {

    private static final Logger logger = LoggerFactory.getLogger(TestServer.class);

    public static void main(String[] args) throws Exception {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        Selector selector = Selector.open();
        SelectionKey selectionKey = serverSocketChannel.register(selector, 0, serverSocketChannel);
        selectionKey.interestOps(SelectionKey.OP_ACCEPT);
        serverSocketChannel.bind(new InetSocketAddress(8080));
        NioEventLoop nioLoop = new NioEventLoop();
        while (true) {
            logger.info("main函数阻塞在这里吧。。。。。。。");
            selector.select();
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectionKeys.iterator();
            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                keyIterator.remove();
                if (key.isAcceptable()) {
                    ServerSocketChannel channel = (ServerSocketChannel)key.channel();
                    //得到客户端的channel
                    SocketChannel socketChannel = channel.accept();
                    //把客户端的channel注册到新线程的selector上
                    nioLoop.register(socketChannel,nioLoop);
                    //连接成功之后，用客户端的channel写回一条消息
                    socketChannel.write(ByteBuffer.wrap("服务端发送成功了".getBytes()));
                }
            }
        }
    }
}

```

启动之后可以完美运行。

```java
[main] INFO nio.server.doserver.ServerBootstrap - main函数阻塞在这里吧。。。。。。。
[main] INFO nio.server.doserver.ServerBootstrap - 新线程创建了！
[Thread-0] INFO nio.server.NioEventLoop - 新线程阻塞在这里3秒吧。。。。。。。
[main] INFO nio.server.doserver.ServerBootstrap - main函数阻塞在这里吧。。。。。。。
[Thread-0] INFO nio.server.doserver.ServerBootstrap - 我没任务了！
[Thread-0] INFO nio.server.SingleThreadEventLoop - 客户端的channel已注册到新线程的多路复用器上了！
[Thread-0] INFO nio.server.NioEventLoop - 新线程阻塞在这里3秒吧。。。。。。。
[Thread-0] INFO nio.server.NioEventLoop - 新线程收到客户端发送的数据:客户端发送成功了
[Thread-0] INFO nio.server.NioEventLoop - 新线程阻塞在这里3秒吧。。。。。。。
[Thread-0] INFO nio.server.doserver.ServerBootstrap - 我没任务了！

```

## **Reactor 线程模型**

好了，这就引出了我们这节课要讲解的主题： **在服务端，由一个线程接收客户端连接，而另一些线程来处理这些客户端连接的 IO 事件，这就是 Reactor 线程模型最直白通俗的解释** 。 有时候我会叫它：瑞啊克特儿。但通常情况下我总是称它为主从线程模型，**主线程组为 bossGroup，从线程组为workerGroup。**

**在 Netty 中 bossGroup 就负责接收客户端的连接** **（** **虽然设置的是主线程组，但干活的实际就一个线程，后面我们会从** **源码** **中学到原理)，workerGroup 则负责把接收到的客户端** **channel** **注册到自身的 slector 上，然后处理收发数据等 IO 事件。简单来说，就是 bossGroup 专门负责接收客户端连接，workerGroup 专门负责处理客户端连接的 IO 事件。** 用这种线程模型来工作效率极高，因为 bossGroup 不必关心接收到的连接是否注册到了从线程组中的 selector 上，只专心接收连接，而 workerGroup 只负责处理 IO 事件，不必关心客户端连接是怎么来的，实现了真正的分工合作。当然，workerGroup 在处理 IO 事件的同时，还处理了其他的一些任务。在后面的课程中，我们会逐渐引入。

实际上到这里，我已经讲完了 Netty 的核心。当然，我说的 Netty 核心也仅仅是我理解的核心。**但是，Netty 的一切不都是建立在这个性能极高的 Reactor 模型上的吗？** 确实，学习 Netty 你会惊叹它的代码如此精妙，责任链如何对数据进行处理，在各个你想象不到的地方，作者是怎么优化代码来追求极致的性能，这些都值得我们花功夫学习。可我还是得说一句，不管 Netty 的其他部分怎么对数据进行处理，对性能怎么优化，难道你发送给女朋友的消息，经过 Netty 的处理，到达她那边会变了意思吗？本来你发送的是“我们吃饭吧”，到了她那边却变成“看我吃饭吧”。会吗？肯定不会，你应该也不希望这样。

所以，我认为收发数据的方式和逻辑就是 Netty 最核心的地方，也就是 Reactor 模型。当然，这个核心的原理最终还是要在代码中得到体现，而代码就是我上面给出的那几个类。在 Netty 的主从线程模型中，不管是主线程或从线程，都是以上面的三个类，NioEventLoop、SingleThreadEventLoop 和 SingleThreadEventExecutor 为核心来展开工作的。虽然类的内容很简洁，但随着课程的深入，你会发现我们逐渐向里面填充了越来越多的东西，直到和源码一致。

## 引入workerGroup

其实，还有一个问题被我们遗漏了，刚才我们一直在说可以创建多个 NIO 线程处理客户端 channel 的 IO 事件，可到目前为止，我们新创建的线程只有一个。

那多个 NIO 线程，也就是 workerGroup 是怎么处理 channel 的 IO 事件的呢？

我们来看下面的代码。只在服务端稍作改动，其他几个类仍然不变。

```java
public class TestServer {

    private static final Logger logger = LoggerFactory.getLogger(TestServer.class);

    public static void main(String[] args) throws Exception {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        Selector selector = Selector.open();
        SelectionKey selectionKey = serverSocketChannel.register(selector, 0, serverSocketChannel);
        selectionKey.interestOps(SelectionKey.OP_ACCEPT);
        serverSocketChannel.bind(new InetSocketAddress(8080));
        //初始化NioEventLoop数组
        NioEventLoop[] workerGroup = new NioEventLoop[2];
        workerGroup[0] = new NioEventLoop();
        workerGroup[1] = new NioEventLoop();
        int i = 0;
        while (true) {
            logger.info("main函数阻塞在这里吧。。。。。。。");
            selector.select();
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectionKeys.iterator();
            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                keyIterator.remove();
                if (key.isAcceptable()) {
                    ServerSocketChannel channel = (ServerSocketChannel)key.channel();
                    //得到客户端的channel
                    SocketChannel socketChannel = channel.accept();
                   //计算要取值的数组的下标
                    int index = i % workerGroup.length;
                    //把客户端的channel注册到新线程的selector上
                    workerGroup[index].register(socketChannel,workerGroup[index]);
                    i++;
                    logger.info("socketChannel注册到了第{}个单线程执行器上：",index);
                    //连接成功之后，用客户端的channel写回一条消息
                    socketChannel.write(ByteBuffer.wrap("服务端发送成功了".getBytes()));
                }
            }
        }
    }
}

```

服务端代码改动也不大，只不过就是创建了两个单线程执行器，并且把创建好的 NioEventLoop 放到了数组中，启动三个客户端。结果如下。

```java
[main] INFO nio.server.doserver.ServerBootstrap - socketChannel注册到了第0个单线程执行器上
[main] INFO nio.server.doserver.ServerBootstrap - socketChannel注册到了第1个单线程执行器上
[main] INFO nio.server.doserver.ServerBootstrap - socketChannel注册到了第0个单线程执行器上

```

看得出来，我们采用轮询的策略将接收到的客户端 channel 分别注册到两个单线程执行器中的 selector 上，明显已经奏效了。从此就由这两个单线程执行器来接管 socketChannel 的读写事件。已经有点样子了是不是？在源码中，你确实可以创建一组单线程执行器，并把它取名为 workerGroup。但是并不是直接创建NioEventLoop 数组。我们要用一个类创建一个新的对象，这个类有一个半新不新的名字：NioEventLoopGroup，这就是我们下节课要讲的内容。并且我可以再告诉你一句，NioEventLoop 数组，其实就是 NioEventLoopGroup 父类中的一个成员变量。
