我们为了进一步提升 NIO 服务端的工作效率，最终需要引入线程池, 将服务端的connect事件和read事件分离，由新的线程Work专门处理read事件

这节课，我们将从最基础的线程池，一步步衍生出可以大大提高 NIO 线程性能的 Reactor 线程模型。

一旦掌握了 Reactor 线程模型，我们的两只脚就已经踏进 Netty 的大门了。

## 引入线程池

要想使用线程池，首先要引入 Executor 接口 。 既然只管理一个线程来处理 IO 事件，那我就给这个 Work 类换个名子：SingleThreadEventExecutor。

Executor 是执行者的意思，所以我更愿意称 SingleThreadEventExecutor 为单线程执行器。 为什么1个新线程也非要用线程池呢？因为我们需要阻塞队列

有了新的类名，我们再补全线程池必须具备的最基本属性：任务队列、拒绝策略、添加任务的方法和执行方法等等。下面就是更新过的代码。

```java
public class SingleThreadEventExecutor implements Executor {

    private static final Logger logger = LoggerFactory.getLogger(SingleThreadEventExecutor.class);

    //任务队列的容量，默认是Integer的最大值
    protected static final int DEFAULT_MAX_PENDING_TASKS = Integer.MAX_VALUE;

    private final Queue<Runnable> taskQueue;

    private final RejectedExecutionHandler rejectedExecutionHandler;

    private volatile boolean start = false;

    private final SelectorProvider provider;

    private Selector selector;

    private Thread thread;

    public SingleThreadEventExecutor() {
        //java中的方法，通过provider不仅可以得到selector，还可以得到ServerSocketChannel和SocketChannel
        this.provider = SelectorProvider.provider();
        this.taskQueue = newTaskQueue(DEFAULT_MAX_PENDING_TASKS);
        this.rejectedExecutionHandler = new ThreadPoolExecutor.AbortPolicy();
        this.selector = openSecector();
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
        System.out.println("我没任务了！");
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
        //从任务队列中拉取任务,如果第一次拉取就为null，说明任务队列中没有任务，直接返回即可
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

    public void register(SocketChannel socketChannel) {
        //如果执行该方法的线程就是执行器中的线程，直接执行方法即可
        if (inEventLoop(Thread.currentThread())) {
            register0(socketChannel);
        }else {
            //在这里，第一次向单线程执行器中提交任务的时候，执行器终于开始执行了,新的线程也开始创建
            this.execute(new Runnable() {
                @Override
                public void run() {
                    register0(socketChannel);
                    logger.info("客户端的channel已注册到新线程的多路复用器上了！");
                }
            });
        }
    }

    private void register0(SocketChannel channel) {
        try {
            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_READ);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
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

    /**
     * @Author: PP-jessica
     * @Description:判断线程是否需要在selector阻塞，或者继续运行的方法，为什么现在需要这个方法了？
     * 因为现在我们新创建的线程不仅要处理io事件，还要处理用户提交过来的任务，如果一直在selector上阻塞着，
     * 显然用户提交的任务也就无法执行了。所以要有限时的阻塞，并且只要用户提交了任务，就要去执行那些任务。
     * 在这里，用户提交的任务就是把客户端channel注册到selector上。
     */
    private void select() throws IOException {
        Selector selector = this.selector;
        //这里是一个死循环
        for (;;){
            //如果没有就绪事件，就在这里阻塞3秒，有限时的阻塞
            logger.info("新线程阻塞在这里3秒吧。。。。。。。");
            int selectedKeys = selector.select(3000);
            //如果有事件或者单线程执行器中有任务待执行，就退出循环
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

    public void run() {
        while (true) {
            try {
                //没有事件就阻塞在这里
                select();
                //如果走到这里，就说明selector没有阻塞了
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

## 服务端程序改造

服务端的代码也有一点小变动，在客户端连接上之后，需要用新线程SingleThreadEventExecutor把客户端的channel注册到新线程的selector上

```java
package com.pp.netty.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;

public class SimpleServer {
    private static final Logger logger = LoggerFactory.getLogger(SimpleServer.class);

    public static void main(String[] args) throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        Selector selector = Selector.open();
        SelectionKey selectionKey = serverSocketChannel.register(selector, 0, serverSocketChannel);
        selectionKey.interestOps(SelectionKey.OP_ACCEPT);
        serverSocketChannel.bind(new InetSocketAddress(8080));
        //创建单线程执行器
        SingleThreadEventExecutor singleThreadEventExecutor = new SingleThreadEventExecutor();
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
                    singleThreadEventExecutor.register(socketChannel);
                    logger.info("客户端在main函数中连接成功！");
                    //连接成功之后，用客户端的channel写回一条消息
                    socketChannel.write(ByteBuffer.wrap("我发送成功了".getBytes()));
                    logger.info("main函数服务器向客户端发送数据成功！");
                }
            }
        }
    }
}
```

客户端程序不变

## 单线程执行器的运行原理

单线程执行器的调用入口是singleThreadEventExecutor.register

在这里，由单线程执行器把主线程接收到的 socketChannel 传进了单线程执行器内部。顺着这个方法的逻辑，我们来到单线程执行器的内部，看看 register 的逻辑

```java
public void register(SocketChannel socketChannel) {
        //如果执行该方法的线程就是执行器中的线程，直接执行方法即可
        if (inEventLoop(Thread.currentThread())) {
            register0(socketChannel);
        }else {
            //在这里，第一次向单线程执行器中提交任务的时候，执行器终于开始执行了,新的线程也开始创建
            this.execute(new Runnable() {
                @Override
                public void run() {
                    register0(socketChannel);
                    logger.info("客户端的channel已注册到新线程的多路复用器上了！");
                }
            });
        }
    }

```

到这里，我们可以看到这个方法：inEventLoop(Thread.currentThread())，这个方法的逻辑就是判断两个线程是否相等。这个方法在 Netty 源码中随处可见，因为它决定了一个任务是否要提交给单线程执行器来执行。

```java
 public boolean inEventLoop(Thread thread) {
        return thread == this.thread;
    }

```

该方法中的 this.thread 属性就是该单线程执行器管理的线程，而该线程只有在被创建的时候才会赋值，并且是由新创建的线程赋值给该属性。而作为方法传递进来的 thread 参数，则是正在执行 register 方法的线程。 所以当执行inEventLoop(Thread.currentThread()) 这个方法的时候，执行当前方法的线程是主线程，而单线程执行器管理的线程还未创建，结果肯定会返回 false。

### 异步execute

所以，代码就会执行到下面的分支，**把 register0 方法封装成异步任务**，提交给单线程执行器去执行。注意，这是我们第一次向单线程执行器提交任务。

```java
 @Override
    public void execute(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        //把任务提交到任务队列中，这里直接把它提交给队列，是考虑到单线程执行器既要处理IO事件（Read）
        //也要执行用户提交的任务，不可能同一时间做两件事。索性就直接先把任务放到队列中。等IO事件处理了
        //再来处理用户任务
        addTask(task);
        //启动单线程执行器中的线程
        startThread();
    }
```

注意，虽然叫execute，但是它首先并没有立即执行注册socketChannel任务而是直接存进阻塞队列等待执行，所以register0依然是异步的

然后开始创建线程startThread

```java
private void startThread() {
        if (start) {
            return;
        }
        start = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                //这里得到了新创建的线程
                thread = Thread.currentThread();
                //执行run方法，在run方法中，就是对io事件的处理
                SingleThreadEventExecutor.this.run();
            }
        }).start();
        logger.info("新线程创建了！");
    }

```

重点来了，在 startThread 方法中，我们创建了新的线程，并且让新的线程去执行一个任务，在该任务中，我们看到了 thread = Thread.currentThread() 这行代码。成员变量thread在这里被初始化。

### 同名 run 方法

紧接着，该线程就去执行单线程执行器中定义的同名 run 方法。所以，重点又来到了同名 run 方法内。不过这时候，大家应该已经清楚 **，在单线程执行器中，新的线程是在外部线程第一次向该执行器提交任务时才创建，并且只创建一次。** 好的，下面我们就来看看同名 run 方法。

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

### select超时阻塞

迎面而来的就是一个 while 循环。这意味着，如果没有意外，我们新创建的线程会在一个循环中获得永生，除非服务器断电了。然后我们就来到了 select 方法中。

```java
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

```

没错，你们会看见在该方法跳出循环的时候，多了一个对 hasTasks 方法的判断。该方法正是用来判断我们单线程执行器的任务队列中是否有任务的，**任务队列中有任务或者** **selector** **有** **IO** **事件，该循环就会被打破。** 但此时我们的 selector 还未被 socketChannel 注册，注定不会有 IO 事件。所以要想跳出该循环，只能期待任务队列中有任务。但是别忘了，我们刚刚创建新线程的时候就向单线程执行器的任务队列中提交了一个注册 socketChannel 到 selector 上的任务。到这里，该方法内的循环就被顺理成章地打破了。

我们再回到同名 run 方法中，接下来 processSelectedKeys(selector.selectedKeys()) 这个方法是处理 IO 事件，但此时 selector 还未被 socketChannel 注册（因为注册行为register0是异步的，此时还为执行），又怎么会有 IO 事件到来呢，所以代码自然走到了最下面的runAllTasks() 方法中，开始执行单线程执行器的任务队列中的所有任务。这时候，新创建的线程才会把 socketChannel 注册到单线程执行器内部的 selector 上。至此，刚才困扰我们的异步注册 socketChannel 的难题终于解决了。如此完美，又如此巧妙。

```java
    /**
     * @Author: PP-jessica
     * @Description:执行任务队列中的所有任务
     */
    protected void runAllTasks() {
        runAllTasksFrom(taskQueue);
    }

    protected void runAllTasksFrom(Queue<Runnable> taskQueue) {
        //从任务队列中拉取任务,如果第一次拉取就为null，说明任务队列中没有任务，直接返回即可
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
```

### NIO逻辑processSelectedKeys

当然，接下来的一切就更加顺理成章了。执行完任务队列中的所有任务，新线程在 while 循环中又会来到 select方法内，

但这次，selector 已经被 socketChannel 注册了，客户端会向服务端发送了一条消息，服务端就会检测到 IO 事件（Read），即便任务队列中没有任务，也会跳出循环

接着向下执行到 processSelectedKeys(selector.selectedKeys()) 。这段代码我们已经写了很多次了吧，服务端在迭代器中处理客户端发送过来的消息，视为read事件

```java
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

```

终于新线程在第二次循环执行processSelectedKeys的时候，客户端发过来的Read消息就在这段代码中被接收了。

接着新创建的线程又会去执行任务队列中的所有任务，但现在已无任务可执行，线程就又会来到 select 方法内。但这次任务队列中既没任务，也没 IO 事件到来，所以新创建的线程就会在 selector.select(3000) 阻塞住，当然，如果一直没有 IO 事件，那新的线程每过 3 秒就会看看任务队列中是否有任务，以此决定是否跳出循环。至此，我们的 SingleThreadEventExecutor 终于讲完了。

现在，让我们再梳理一下细节。**我们创建了一个单线程执行器，该执行器会管理一个线程，并且该执行器持有一个** **selector** **，当主线程接收到客户端的** **channel** **后，会把将 socketChannel 注册到 selector 封装成一个任务提交给单线程执行器。并且第一次提交任务的时候，单线程执行器内部的线程开始创建，然后单线程执行器开始工作。由此可见，一个执行器管理一个线程，并且持有一个 selector，** 这一点应该十分清楚了。
