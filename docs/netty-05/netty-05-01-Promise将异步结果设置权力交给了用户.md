上节课我为大家展示了围绕着 EventLoop、EventExecutor 和 Group 衍生出的一些类和接口，并且详细梳理了它们之间的关系

接下来我们的课程开始进入并发编程的领域了，所以，为了协调各个线程更好地工作，引入 Promise 实现线程间的通信显然是很有必要的。

Promise 虽然是 Netty 独有的协调多线程工作的功能，但其本质仍然可以追溯到 Java 的 Future 上。因为 Promise 这个接口实际上继承了 Java 的 Future 接口，并在其之上扩展了一些更为方便的功能。

所以，这节课我们将以一个 Java 的 Future 小例子为起点，通过分析和对比，渐渐完善出一个功能齐全的 Promise。当然，我们的例子只会实现核心功能，在和这节课对应的手写第三版本代码中，大家将会看到功能更为齐全的实现。
## 回顾 FutureTask
Future 这个接口以及它的重要实现类 FutureTask，大家应该都很清楚了，这是 Java 并发编程中最基础的知识。请看下面给出的这段代码。

首先创建了一个有返回值的 Callable，然后将 Callable 传入一个 FutureTask 中。
```java
public class FutureTest {
    public static void main(String[] args) throws InterruptedException {
        //创建一个Callable
        Callable<Integer> callable = new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return 1314;
            }
        };
        //创建一个FutureTask，把任务传进FutureTask中
        FutureTask<Integer> future = new FutureTask<Integer>(callable);
        //创建一个线程
        Thread t = new Thread(future);
        t.start()
        //无超时获取结果
        System.out.println(future.get());
    }
}
```
在上面的代码中，大家可以看到 FutureTask 的简单用法。如果 future.get() 获取不到返回结果，主线程就会在这里阻塞，直到结果返回，主线程才会继续向下运行.

第二种方式是把包装有Callable的FutureTask交给线程池执行：
```java
public class FutureTest {
    public static void main(String[] args) throws InterruptedException {
        //创建一个Callable
        Callable<Integer> callable = new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return 1314;
            }
        };
        //创建一个FutureTask
        FutureTask<Integer> future = new FutureTask<Integer>(callable);
        //创建一个线程池
        ExecutorService threadPool = Executors.newCachedThreadPool();
        Future<?> otherFuture = threadPool.submit(future);
        //无超时获取结果
        System.out.println(otherFuture.get());
    }
}
```

可以看到线程池内部会额外创建一个Future给到用户，因此就有了第三种方案，直接将Callable传给线程池，不用包装FutureTask了，线程池会为我们创建好FutureTask
```java
public class FutureTest {
    public static void main(String[] args) throws InterruptedException {
        //创建一个Callable
        Callable<Integer> callable = new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return 1314;
            }
        };
        //创建一个线程池
        ExecutorService threadPool = Executors.newCachedThreadPool();
        Future<?> future = threadPool.submit(callable);
        //无超时获取结果
        System.out.println(future.get());
    }
}
```
下面就是 Java 线程池内部的具体逻辑。
```java
public abstract class AbstractExecutorService implements ExecutorService {
    
    ......
    
    public Future<?> submit(Runnable task) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<Void> ftask = newTaskFor(task, null);
        execute(ftask);
        return ftask;
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <T> Future<T> submit(Runnable task, T result) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<T> ftask = newTaskFor(task, result);
        execute(ftask);
        return ftask;
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <T> Future<T> submit(Callable<T> task) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<T> ftask = newTaskFor(task);
        execute(ftask);
        return ftask;
    }


    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new FutureTask<T>(callable);
    }
    
    ......
}
```

可以看到，在 AbstractExecutorService 抽象类中的三个 submit 方法，其实是两种类型
- 一种类型是传Runnable接口，正好Future接口继承了Runnable接口，RunnableFuture 接口继承了 Future 接口，而FutureTask实现了Runnable接口
- 一种类型是传Callable接口

而且不管用户传进来的参数是什么类型的，都会在方法内部执行 RunnableFuture ftask = newTaskFor(task) 这样一行代码。 

然后将RunnableFuture交给 execute 方法来执行，这时候，FutureTask 实际上就在发挥 Runnable 任务身份的作用。

既然是一个 Runnable，那我们就来看看它内部 run 方法的逻辑。
```java
public class FutureTask<V> implements RunnableFuture<V> {

    ......
    
     //用户传进来的要被执行的又返回值的任务
    private Callable<V> callable;

    //返回值要赋值给该成员变量
    private Object outcome;

    ......
    
    public FutureTask(Runnable runnable, V result) {
        //构造方法，在该方法内，如果传入的是Runnable类型的对象
        //会被下面的方法包装成一个Callable对象
        this.callable = Executors.callable(runnable, result);
         ......
    }


     @Override
    public void run() {
        
        ......
       //得到callable
       Callable<V> c = callable;
        //执行callable，得到返回值
       result = c.call();
       //走到这就意味着任务正常结束，可以正常把执行结果赋值给成员变量outcome
       set(result);  
        
        ......
    
    }

    
        protected void set(V v) {
        ...
        outcome = v;
        ...
    }

    
    ......


    
}
```

在该类的 run 方法中，实际上真正执行的是 Callable 中的 call 方法，得到 Callable 的返回结果后，会调用 set 方法将该结果赋值给该类的 outcome 成员变量。

如果外部线程在成员变量 outcome 还未被赋值之前就调用了 future.get() 方法，那外部线程就会阻塞，直到真正执行 Callable 中的 call 方法的线程执行完毕后，外部线程（main）才会继续向下执行。

这就是 并发编程 中最直接的线程交流的手段。而所谓的线程交流，就是一个线程 必 须等待另一个线程的执行结果，方可继续执行。


到此为止，如果通过上面的一个小例子，大家已经顺利回忆起和 FutureTask 有关的一切，那么下面我想为大家正式引入 Promise 接口。因为在 Netty 中，Promise 接口的实现类 DefaultPromise 的具体使用方式，和 FutureTask 并没有太大的区别。从下面这段代码就能看出来。
```java
 public class ServerBootstrap {
     
     ......
     
      public DefaultPromise<Object> bind(String host, int inetPort) {
        return bind(new InetSocketAddress(host, inetPort));
    }

     public DefaultPromise<Object> bind(SocketAddress localAddress) {
        return doBind(localAddress);
    }

      private DefaultPromise<Object> doBind(SocketAddress localAddress) {
        
          //之前的逻辑暂且省略......
          
        nioEventLoop.register(serverSocketChannel,nioEventLoop);
        DefaultPromise<Object> defaultPromise = new DefaultPromise<>(nioEventLoop);
        //执行真正的绑定端口号方法
        doBind0(localAddress,defaultPromise);
        return defaultPromise;
    }
     ......
     
 }
```
我截取了第三版本手写代码的一小部分，可以看到，在服务端的启动类中，bind 方法的返回值就是一个 Promise 接口的实现类 DefaultPromise，沿着调用链一路查看到 doBind 方法内，会发现在 18 行创建了一个 DefaultPromise，并且返回给用户。

这就是它和 FutureTask 的相同之处。当然，有些朋友可能会说在 Netty 源码中返回的是 ChannelFuture（ChannelFuture 也是 Promise 体系中的一部分）。 确实如此，这一点在下一节课，我们引入 Channel 之后才会真正实现。

但是不管返回的是什么，整个 Promise 体系的核心功能全都是在 DefaultPromise 类中实现的。所以，我们的重点自然是放在该类中。

## 定义自己的 Promise 接口框架
在前面我说过，Promise 实际上就是继承了 Java 的 Future 接口。回到我们自己要实现的 Promise，它内部究竟该怎么定义呢？既然它继承了 Java 的 Future 接口，那我们就从这里入手，实现一个 Promise。

下面是 Java 的 Future 接口。
```java
public interface Future<V> {

    boolean cancel(boolean mayInterruptIfRunning);

    boolean isCancelled();

    boolean isDone();

    V get() throws InterruptedException, ExecutionException;

    V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;
}
```
接着是 Promise 接口。
```java
public interface Promise<V> extends Runnable, Future<V> {

    @Override
    void run();
    
}
```
在上面，Promise 接口就把 Future 接口中的所有方法都继承了。然后是其真正的实现类 DefaultPromise。
```java
public class DefaultPromise<V> implements Promise<V> {
    //方法暂时还不实现。
}
```
这就是我们暂且定义好的类和接口的简单关系。现在，我们应该向 DefaultPromise 类中填充具体内容了。

首先还是参照 FutureTask，把 DefaultPromise 缺失的内容照搬过来。比如，我们也需要一个等待赋值的成员变量，我们就把它定义成 result。还有一个用于得到返回值的 Callable。像下面这样。
```java
public class DefaultPromise<V> implements Promise<V> {
    //执行后得到的结果要赋值给该属性
    private volatile Object result;
    //用户传进来的要被执行的又返回值的任务
    private Callable<V> callable;

     @Override
    public void run() {
        V object;
       //得到callable
       Callable<V> c = callable;
        //执行callable，得到返回值
       object = c.call();
       //走到这就意味着任务正常结束，可以正常把执行结果赋值给成员变量outcome
       set(object); 
    
    }

    protected void set(V v) {
        result = v;
    }

    @Override
    public V get() throws InterruptedException, ExecutionException{
        //说明这时候没有结果
        if (result == null) {
            //就要等待，这个等待，指的是外部调用get方法的线程等待
            await();
        }
        return getNow();
    }
    
     //等待结果的方法
    public Promise<V> await() throws InterruptedException {
        //暂时不做实现，只返回自身
        return this；
    }

    public V getNow() {
        Object result = this.result;
        return (V) result;
    }


    //先暂且实现这几个方法，接口中的其他方法，等需要的时候再做实现
}
```
当我们创建了一个 DefaultPromise 对象，根据类和接口的关系，实际上我们就是创建了一个 Runnable.

考虑到在 Netty 中都是使用单线程执行器来执行异步任务，所以这里我们就创建一个线程，来执行这个 Runnable。这样情况会简单很多。请看下面这段代码。
```java
public class DefaultPromiseTest {
    public static void main(String[] args) throws InterruptedException {
        //创建一个Callable
        Callable<Integer> callable = new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                //睡眠一会
                Thread.sleep(5000);
                return 1314;
            }
        };
        //创建一个DefaultPromise，把任务传进DefaultPromise中
        Promise<Integer> promise = new DefaultPromise<Integer>(callable);
        //创建一个线程
        Thread t = new Thread(promise);
        t.start()
        //无超时获取结果
        System.out.println(promise.get());
    }
}
```
当我们用一个线程去执行 DefaultPromise 任务时，如果我们在 Callable 任务中睡眠 5 秒，那主线程调用 promise.get 方法后，就会在 get 方法内执行到 await 方法，也会阻塞 5 秒，然后才继续向下执行，返回执行结果。

但是，这只是我们的理想愿望，毕竟在 DefaultPromise 中，await 方法还未被实现，而让 main 函数线程阻塞的核心逻辑就在该方法中。 那该怎么实现呢？

这时候，我又想起了 Java 中的 FutureTask，想去看看它是怎么实现让外部线程阻塞的。于是，我找到了这部分逻辑的核心代码。请看下面的代码。
```java
public class FutureTask<V> implements RunnableFuture<V> {

    ......


    //第一个参数，是允许限时阻塞，如果是false，就一直阻塞，等待执行结果返回才继续运行
    //如果为true，则根据传入的时间，限时阻塞
    private int awaitDone(boolean timed, long nanos)throws InterruptedException {
        final long deadline = timed ? System.nanoTime() + nanos : 0L;
        WaitNode q = null;
        boolean queued = false;
        for (;;) {
            //判断当前线程是否被中断，中断了就将该线程从等待队列中移除，这里的线程指的是外部调用get方法的线程
            if (Thread.interrupted()) {
                removeWaiter(q);
                throw new InterruptedException();
            }

            int s = state;
            //这时候说明任务已经完成了
            if (s > COMPLETING) {
                if (q != null) {
                    q.thread = null;
                }
                return s;
            }
            else if (s == COMPLETING) {
                Thread.yield();
            } else if (q == null) {
                //在这里q被创建了，把外部调用get方法的线程封装到WaitNode节点中
                //该节点会被添加到一个队列中，实际上，所有在此阻塞的外部线程都会被包装成WaitNode节点
                //添加到队列中。
                q = new WaitNode();
            } else if (!queued) {
                //这里就是在队列头部搞了一个头节点，头节点就是最晚进来的那个线程，当然，线程都被WaitNode包装着
                //头节点实际上就是WaitNode对象。越晚进来的线程会排在链表的头部，谁最晚进来，谁就是头节点
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset,
                        q.next = waiters, q);
            } else if (timed) {
                nanos = deadline - System.nanoTime();
                if (nanos <= 0L) {
                    removeWaiter(q);
                    return state;
                }
                //设置外部线程有时限的阻塞
                LockSupport.parkNanos(this, nanos);
            }
            else {
                //到这里是一直阻塞，可以看到阻塞采用的是 LockSupport.park方式
                LockSupport.park(this);
            }
        }
    }


    //在这方法中，把等待队列中的线程唤醒，也就是那些调用了get方法，然后阻塞在该方法处的外部线程
    private void finishCompletion() {
        for (WaitNode q; (q = waiters) != null;) {
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
                for (;;) {
                    Thread t = q.thread;
                    if (t != null) {
                        q.thread = null;
                        //在此处唤醒外部线程
                        LockSupport.unpark(t);
                    }
                    WaitNode next = q.next;
                    if (next == null) {
                        break;
                    }
                    q.next = null;
                    q = next;
                }
                break;
            }
        }

        done();

        callable = null;
    }


    ......

    
}
```
在 FutureTask 的源码中，让外部线程阻塞的逻辑用的是JUC中的LockSupport，概括地说
- 在 outcome 成员变量尚未被赋值之前，它的做法是将每一个调用了 future.get 方法的外部线程包装成了一个个 WaitNode 节点，由这些节点构成了一个链表，最后调用 LockSupport.park 方法完成外部线程阻塞。 
- 等条件达到了，这些被阻塞的外部线程又会在 finishCompletion 方法内被 LockSupport.unpark 方法依次唤醒。

听起来逻辑就比较复杂，对吧？显然达不到我想要的简单直接的效果。

那么，在并发编程中，除了 LockSupport.park 可以使线程阻塞，还有其他方法具有同样效果吗？首先，我就想到了 wait 和 notify 方法。

既然我不想使简单问题复杂化，那我为何不直接采取 wait 和 notify 配合 synchronized 来直接实现线程的阻塞和唤醒？当然，考虑到可能会有多个外部线程同时调用 promise.get 方法，所以我们可以把 notify 换成 notifyAll，唤醒所有在此方法阻塞的外部线程。

下面就是我们重构之后的 DefaultPromise 类。
```java
public class DefaultPromise<V> implements Promise<V> {
    //执行后得到的结果要赋值给该属性
    private volatile Object result;
    //用户传进来的要被执行的又返回值的任务
    private Callable<V> callable;
     //这个成员变量的作用很简单，当有一个外部线程在await方法中阻塞了，该属性就加1，每当一个外部
    //线程被唤醒了，该属性就减1.简单来说，就是用来记录阻塞的外部线程数量的
    //在我们手写的代码和源码中，这个成员变量是Short类型的，限制阻塞线程的数量，线程是宝贵的资源
    //如果阻塞的线程太多就报错，这里我们只做简单实现，具体逻辑可以从我们手写的代码中继续学习
    private int waiters;

     @Override
    public void run() {
        V object;
       //得到callable
       Callable<V> c = callable;
        //执行callable，得到返回值
       object = c.call();
       //走到这就意味着任务正常结束，可以正常把执行结果赋值给成员变量outcome
       set(object); 
    
    }

    protected void set(V v) {
        result = v;
        //唤醒被阻塞的外部线程
        checkNotifyWaiters();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException{
        //说明这时候没有结果
        if (result == null) {
            //就要阻塞等待，这个等待，指的是外部调用get方法的线程等待
            await();
        }
        return getNow();
    }
    
        //等待结果的方法
    public Promise<V> await() throws InterruptedException {
        //如果已经执行完成，直接返回即可
        if (isDone()) {
            return this;
        }
        //如果线程中断，直接抛出异常
        if (Thread.interrupted()) {
            throw new InterruptedException(toString());
        }
        //wait要和synchronized一起使用，在futurtask的源码中
        //这里使用了LockSupport.park方法
        synchronized (this) {
            //如果成功赋值则直接返回，不成功进入循环
            while (!isDone()) {
                //waiters字段加一，记录在此阻塞的线程数量
                ++waiters;
                try {
                    //释放锁并等待
                    wait();
                } finally {
                    //等待结束waiters字段减一
                    --waiters;
                }
            }
        }
        return this;
    }

      //检查并且唤醒阻塞线程的方法
     private synchronized void checkNotifyWaiters() {
        if (waiters > 0) {
            notifyAll();
        }
    }

    
    public V getNow() {
        Object result = this.result;
        return (V) result;
    }


    //任务是否已经执行完成，也就是判断result成员变量是否被赋值了
    @Override
    public boolean isDone() {
        return isDone0(result);
    }

    private static boolean isDone0(Object result) {
        return result != null;
    }


    //先暂且实现这几个方法，接口中的其他方法，等需要的时候再做实现
}
```
DefaultPromise 经过一番改造，成了现在的样子，当我们再次使用上面的测试类执行 DefaultPromise 中的异步任务时，通过 await 方法内的 wait 方法和 checkNotifyWaiters 方法内的 notifyAll 方法，唤醒和阻塞外部线程的功能就可以随意使用了。

但是，我们也不能高兴太早，因为还有一个功能没有实现。对照 FutureTask，别忘了它内部的 LockSupport.parkNanos(this, nanos) 这个方法，限时获取返回结果，我们的 DefaultPromise 是不是应该同样具备有限时地获取返回值的功能呢？

所以，就让我们仍以 wait 和 notifyAll 的方式，实现有限时获取异步任务返回值的功能。请看下面的代码。
```java
public class DefaultPromise<V> implements Promise<V> {
    //执行后得到的结果要赋值给该属性
    private volatile Object result;
    //用户传进来的要被执行的又返回值的任务
    private Callable<V> callable;
     //这个成员变量的作用很简单，当有一个外部线程在await方法中阻塞了，该属性就加1，每当一个外部
    //线程被唤醒了，该属性就减1.简单来说，就是用来记录阻塞的外部线程数量的
    //在我们手写的代码和源码中，这个成员变量是Short类型的，限制阻塞线程的数量，如果阻塞的
    //线程太多就报错，这里我们只做简单实现，具体逻辑可以从我们手写的代码中继续学习
    private int waiters;

     @Override
    public void run() {
        V object;
       //得到callable
       Callable<V> c = callable;
        //执行callable，得到返回值
       object = c.call();
       //走到这就意味着任务正常结束，可以正常把执行结果赋值给成员变量outcome
       set(object); 
    
    }

    protected void set(V v) {
        result = v;
        //唤醒被阻塞的外部线程
        checkNotifyWaiters();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException{
        //说明这时候没有结果
        if (result == null) {
            //就要阻塞等待，这个等待，指的是外部调用get方法的线程等待
            await();
        }
        return getNow();
    }

    //有限时地获取任务的返回结果
     @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        //阻塞了用户设定的时间之后
        if (await(timeout, unit)) {
            //直接返回任务的执行结果
             return getNow();      
        }
        }
    
        //等待结果的方法
    public Promise<V> await() throws InterruptedException {
        //如果已经执行完成，直接返回即可
        if (isDone()) {
            return this;
        }
        //如果线程中断，直接抛出异常
        if (Thread.interrupted()) {
            throw new InterruptedException(toString());
        }
        //wait要和synchronized一起使用，在futurtask的源码中
        //这里使用了LockSupport.park方法
        synchronized (this) {
            //如果成功赋值则直接返回，不成功进入循环
            while (!isDone()) {
                //waiters字段加一，记录在此阻塞的线程数量
                ++waiters;
                try {
                    //释放锁并等待
                    wait();
                } finally {
                    //等待结束waiters字段减一
                    --waiters;
                }
            }
        }
        return this;
    }

    //有限时地等待结果的方法
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return await0(unit.toNanos(timeout), true);
    }

    //这个方法虽然很长，但是逻辑都很简单
     private boolean await0(long timeoutNanos, boolean interruptable) throws InterruptedException {
        //执行成功则直接返回
        if (isDone()) {
            return true;
        }
        //传入的时间小于0则直接判断是否执行完成
        if (timeoutNanos <= 0) {
            return isDone();
        }
        //interruptable为true则允许抛出中断异常，为false则不允许，判断当前线程是否被中断了
        //如果都为true则抛出中断异常
        if (interruptable && Thread.interrupted()) {
            throw new InterruptedException(toString());
        }
        //获取当前纳秒时间
        long startTime = System.nanoTime();
        //用户设置的等待时间
        long waitTime = timeoutNanos;
        for (;;) {
            synchronized (this) {
                //再次判断是否执行完成了
                if (isDone()) {
                    return true;
                }
                //如果没有执行完成，则开始阻塞等待，阻塞线程数加一
                 ++waiters;
                try {
                    //阻塞在这里
                    wait(timeoutNanos);
                } finally {
                    //阻塞线程数减一
                    --waiters;
                }
            }
            //走到这里说明线程被唤醒了，或者被提前唤醒了
            if (isDone()) {
                return true;
            } else {
                //可能是虚假唤醒。
                //System.nanoTime() - startTime得到的是经过的时间
                //得到新的等待时间，如果等待时间小于0，表示已经阻塞了用户设定的等待时间。如果waitTime大于0，则继续循环
                waitTime = timeoutNanos - (System.nanoTime() - startTime);
                if (waitTime <= 0) {
                    return isDone();
                }
            }
        }
    }

      //检查并且唤醒阻塞线程的方法
     private synchronized void checkNotifyWaiters() {
        if (waiters > 0) {
            notifyAll();
        }
    }

    //直接返回任务的执行结果，如果result未被赋值，则直接返回null
    public V getNow() {
        Object result = this.result;
        return (V) result;
    }


    //任务是否已经执行完成，也就是判断result成员变量是否被赋值了
    @Override
    public boolean isDone() {
        return isDone0(result);
    }

    private static boolean isDone0(Object result) {
        return result != null;
    }


    //先暂且实现这几个方法，接口中的其他方法，等需要的时候再做实现
}
```
在添加了 get(long timeout, TimeUnit unit) 方法、await(long timeout, TimeUnit unit) 方法和 await0(long timeoutNanos, boolean interruptable) 方法之后，我们有限时地获取异步任务返回值的功能终于实现了。

具体逻辑我都标注在代码中了，非常详细，所以就不在文章中详细展开了。现在，我们再把测试类搬运到这里，为以上的内容做一个小总结。
```java
public class DefaultPromiseTest {
    public static void main(String[] args) throws InterruptedException {
        //创建一个Callable
        Callable<Integer> callable = new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                //睡眠一会
                Thread.sleep(5000);
                return 1314;
            }
        };
        //创建一个DefaultPromise，把任务传进DefaultPromise中
        Promise<Integer> promise = new DefaultPromise<Integer>(callable);
        //创建一个线程
        Thread t = new Thread(promise);
        t.start()
        //无超时获取结果
        System.out.println(promise.get());
        //有超时获取结果
        System.out.println(promise.get(500, TimeUnit.MILLISECONDS));
    }
}
```

当我们在主线程中调用的是 promise.get() 方法时，整个程序的调用流程如下图所示。

![promise.get().png](promise.get%28%29.png)

当我们在主线程中调用 promise.get(500, TimeUnit.MILLISECONDS) 方法时，整个程序调用流程如下图所示。

![promise.get(time).png](promise.get%28time%29.png)

这两幅简图就为大家把外部线程的阻塞和唤醒这一块的知识点概括全了。

当然，也有一种情况，外部线程在有超时的情况下阻塞了，但还未到达阻塞时间异步任务就有结果了，那外部线程就会被直接唤醒，继续执行。

## 分析 DefaultPromise 的缺陷
进行到此，按理说我们手写的 DefaultPromise 已经比较完美了，和 FutureTask 对比，功能也算齐全。但是看我们手写的 DefaultPromise ，几乎就是 FutureTask 的复制品。如果我辛苦半天，结果就整出来一个 FutureTask 的复制品，那我的努力有什么价值呢？我完全可以直接使用 FutureTask 呀。

所以，我意识到我们手写的 DefaultPromise 存在着一些弊端，尤其是配合 Netty 使用的时候，这恰好就是我可以对其改进的地方。

现在，请大家看下面一段代码。
```java
public class NettyTest {

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        //创建一个selector
        Selector selector = Selector.open();
        //创建一个callable，异步任务
        Callable callable = new Callable() {
            @Override
            public Object call() throws Exception {
                //创建一个服务端的通道
                ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
                //将该channel注册到selector上,关注接收事件
                serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
                //返回该服务端channel
                return serverSocketChannel;
            }
        };
        DefaultPromise<ServerSocketChannel> promise = new DefaultPromise(callable);
        //异步执行callable任务
        Thread thread = new Thread(promise);
        //启动线程
        thread.start();
        //得到服务端的channel
        ServerSocketChannel serverSocketChannel = promise.get();
        //服务端channel绑定端口号
        serverSocketChannel.bind(new InetSocketAddress("127.0.0.1",8080));
    }
}
```
这段代码的逻辑十分简单，第一节课我们就写过这样的代码，这里之所以把它拿过来，是希望大家可以结合这段代码思考一个问题，

在代码的第 24 行，也就是 ServerSocketChannel serverSocketChannel = promise.get() 这行代码，这里我们得到了服务端的 channel，但是，我们真的需要由异步任务返回服务端的 channel 吗？

实际上，我们创建的这个异步任务，其中关键的逻辑只是把服务端 channel 注册到多路复用器 selector 上。 

我们需要的只是这个步骤而已，因为在服务端 channel 和端口号绑定之前，我们必须保证 channel 已经注册到多路复用器上了。

所以，上面这个测试类中的代码我们其实可以写成这样。
```java
public class NettyTest {

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        //创建一个selector
        Selector selector = Selector.open();
         //创建一个服务端的通道
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        //创建一个callable，异步任务
        Callable callable = new Callable() {
            @Override
            public Object call() throws Exception {
                //将channel注册到selector上,关注接收事件
                serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
                //返回该服务端channel
                return serverSocketChannel;
            }
        };
        DefaultPromise<ServerSocketChannel> promise = new DefaultPromise(callable);
        //异步执行callable任务
        Thread thread = new Thread(promise);
        //启动线程
        thread.start();
        //得到服务端的channel
        ServerSocketChannel serverSocketChannel = promise.get();
        //服务端channel绑定端口号
        serverSocketChannel.bind(new InetSocketAddress("127.0.0.1",8080));
    }
}
```
改动之后的测试类一定会让大家感到滑稽，明明我们已经在 main 函数中创建了一个服务端 channel，结果又在第 15 行，从异步任务中返回该 channel。 这么编写代码肯定会让别人怀疑我们的智商。

可如果我取消了 Callable 中的返回值，我们的代码就会报错。而且还有个问题是如果没有了返回值，我们根据什么来判断 DefaultPromise 中的任务是否执行完了呢？

在 Java 的 FutureTask 中又是怎么判断其任务执行结束了呢？请看下面这段代码。
```java
public class FutureTask<V> implements RunnableFuture<V> {

    //状态值，表示的就是该FutureTask执行到哪一步了
    private volatile int state;
    //初始状态
    private static final int NEW          = 0;
    //正在赋值，还没有彻底完成
    private static final int COMPLETING   = 1;
    //已经正常完成了
    private static final int NORMAL       = 2;
    //执行过程中出现异常
    private static final int EXCEPTIONAL  = 3;
    //取消该任务
    private static final int CANCELLED    = 4;
    //中断线程，但是不是直接中断线程，而是设置一个中断变量，线程还未中断
    private static final int INTERRUPTING = 5;
    //任务已经被打断了
    private static final int INTERRUPTED  = 6;

    //用户传进来的要被执行的有返回值的任务
    private Callable<V> callable;

    //返回值要赋值给该成员变量
    private Object outcome;

    private volatile Thread runner;
    //是一个包装线程的对象。并且是链表的头节点
    private volatile WaitNode waiters;
    
    ......
}
```

这是我从 Java 源码中截取的一段代码，答案其实已经很明显了，Java 中的 FutureTask 其实在自己内部定义了一个状态，用来表示该 FutureTask 执行到哪一步了。

所以，到此大家应该也明白了，实际上 FutureTask 并不是完全依靠其内部的成员变量 outcome 是否被赋值来判断任务结束与否，而是通过内部定义的几种状态值判断的。

这么做显然也十分麻烦，我们自己手写的 DefaultPromise 应该另辟蹊径，采取一种更简洁高效的方式。

如果我手写的 DefaultPromise 要执行的是个 Runnable 任务，那我就自己给它设定一个返回值，比如在类中定义一个 Object 类型的成员变量，命名为 SUCCESS，如果 Runnable 类型的任务执行成功了，就把 SUCCESS 对象赋值给 result 成员变量。

但这么做又会招来新的麻烦，我们现在实现的 DefaultPromise 只能接收 Callable 类型的异步任务，如果要执行没有返回值的 Runnable 任务，DefaultPromise 的构造器就要像 FutureTask 那样改动，增添一个功能，可以将 Runnable 对象包装成 Callable 对象。

或者干脆直接在 DefaultPromise 类中增加一个新的 Runnable 成员变量，但这样一来，该类的构造器以及许多已经完美实现的方法就要重构，增加一些判断，因为传进来的对象可能是 Runnable 和 Callable 中的任何一个。

不管是哪种改动，都不符合我的意愿。 其实，我们的异步任务实际上并不需要什么返回值，回想一下上面 NettyTest 这个小例子，只要我们确保异步执行的 register 方法把服务端 channel 注册到 selecotr 上了，然后再执行 bind 方法即可。

然后让我们再进一步思考一下，Netty 是个全异步的框架，主线程启动了程序，之后大量的工作都在单线程执行器中SingleThreadEventExecutor完成，并且提交给单线程执行器的都是 Runnable 任务。

如果是这样，那我可不可以这样考虑，既然 Netty 会主动为单线程执行器创建 Runnable 对象，那我们的 DefaultPromise 实际上就没必要继续扮演 Runnable 的角色，何必多此一举呢？

也就是说，我们可以不再让 Promise 接口继承 Runnable 接口。DefaultPromise 中的 run 方法自然也就不再需要。

当然我们会添加新的功能，比如，**我会让用户自己设定 DefaultPromise 类中 result 成员变量的值**。我们可以让 Promise 绑定一个异步任务，任务成功执行完了，就可以给 DefaultPromise 的 result 赋值为 SUCCESS。最后仍然是在 result 被赋值之后唤醒其他阻塞的外部线程。

总的来说，我们要达成的效果就是让 Promise 成为异步任务的一部分，而不是成为异步任务本身。下面，我们就按照这个思路来重构一下 DefaultPromise。

## 重构 DefaultPromise
仍然是从接口开始展示，顶层接口 Future 不用变动。
```java
public interface Future<V> {

    boolean cancel(boolean mayInterruptIfRunning);

    boolean isCancelled();

    boolean isDone();

    V get() throws InterruptedException, ExecutionException;

    V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;
}
```
去除 Promise 的 Runnable 身份。
```java
public interface Promise<V> extends Future<V> {
    
}
```
最终的DefaultPromise
```java
public class DefaultPromise<V> implements Promise<V> {
    //执行后得到的结果要赋值给该属性
    private volatile Object result;
     //这个成员变量的作用很简单，当有一个外部线程在await方法中阻塞了，该属性就加1，每当一个外部
    //线程被唤醒了，该属性就减1.简单来说，就是用来记录阻塞的外部线程数量的
    //在我们手写的代码和源码中，这个成员变量是Short类型的，限制阻塞线程的数量，如果阻塞的
    //线程太多就报错，这里我们只做简单实现，具体逻辑可以从我们手写的代码中继续学习
    private int waiters;
    
    //该属性是为了给result赋值，前提是promise的返回类型为void
    //这时候把该值赋给result，如果有用户定义的返回值，那么就使用用户
    //定义的返回值
    private static final Object SUCCESS = new Object();

    //promise和future的区别就是，promise可以让用户自己设置成功的返回值，
    //也可以设置失败后返回的错误
    public Promise<V> setSuccess(V result) {
        if (setSuccess0(result)) {
            return this;
        }
        throw new IllegalStateException("complete already: " + this);
    }

    private boolean setSuccess0(V result) {
        //设置成功结果，如果结果为null，则将SUCCESS赋值给result
        return set(result == null ? SUCCESS : result);
    }


    protected void set(V v) {
        result = v;
        //唤醒被阻塞的外部线程
        checkNotifyWaiters();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException{
        //说明这时候没有结果
        if (result == null) {
            //就要阻塞等待，这个等待，指的是外部调用get方法的线程等待
            await();
        }
        return getNow();
    }

    //有限时地获取任务的返回结果
     @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        //阻塞了用户设定的时间之后
        if (await(timeout, unit)) {
            //直接返回任务的执行结果
             return getNow();      
        }
        }

    //服务器和客户端经常会调用该方法同步等待结果
    public Promise<V> sync() throws InterruptedException {
        await();
        return this;
    }
    
    //等待结果的方法
    public Promise<V> await() throws InterruptedException {
        //如果已经执行完成，直接返回即可
        if (isDone()) {
            return this;
        }
        //如果线程中断，直接抛出异常
        if (Thread.interrupted()) {
            throw new InterruptedException(toString());
        }
        //wait要和synchronized一起使用，在futurtask的源码中
        //这里使用了LockSupport.park方法。
        synchronized (this) {
            //如果成功赋值则直接返回，不成功进入循环
            while (!isDone()) {
                //waiters字段加一，记录在此阻塞的线程数量
                ++waiters;
                try {
                    //释放锁并等待
                    wait();
                } finally {
                    //等待结束waiters字段减一
                    --waiters;
                }
            }
        }
        return this;
    }

    //有限时地等待结果的方法
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return await0(unit.toNanos(timeout), true);
    }

    //这个方法虽然很长，但是逻辑都很简单
     private boolean await0(long timeoutNanos, boolean interruptable) throws InterruptedException {
        //执行成功则直接返回
        if (isDone()) {
            return true;
        }
        //传入的时间小于0则直接判断是否执行完成
        if (timeoutNanos <= 0) {
            return isDone();
        }
        //interruptable为true则允许抛出中断异常，为false则不允许，判断当前线程是否被中断了
        //如果都为true则抛出中断异常
        if (interruptable && Thread.interrupted()) {
            throw new InterruptedException(toString());
        }
        //获取当前纳秒时间
        long startTime = System.nanoTime();
        //用户设置的等待时间
        long waitTime = timeoutNanos;
        for (;;) {
            synchronized (this) {
                //再次判断是否执行完成了
                if (isDone()) {
                    return true;
                }
                //如果没有执行完成，则开始阻塞等待，阻塞线程数加一
                 ++waiters;
                try {
                    //阻塞在这里
                    wait(timeoutNanos);
                } finally {
                    //阻塞线程数减一
                    --waiters;
                }
            }
            //走到这里说明线程被唤醒了
            if (isDone()) {
                return true;
            } else {
                //可能是虚假唤醒。
                //System.nanoTime() - startTime得到的是经过的时间
                //得到新的等待时间，如果等待时间小于0，表示已经阻塞了用户设定的等待时间。如果waitTime大于0，则继续循环
                waitTime = timeoutNanos - (System.nanoTime() - startTime);
                if (waitTime <= 0) {
                    return isDone();
                }
            }
        }
    }

      //检查并且唤醒阻塞线程的方法
     private synchronized void checkNotifyWaiters() {
        if (waiters > 0) {
            notifyAll();
        }
    }

    //直接返回任务的执行结果，如果result未被赋值，则直接返回null
    public V getNow() {
        Object result = this.result;
        return (V) result;
    }


    //任务是否已经执行完成，也就是判断result成员变量是否被赋值了
    @Override
    public boolean isDone() {
        return isDone0(result);
    }

    private static boolean isDone0(Object result) {
        return result != null;
    }


    //先暂且实现这几个方法，接口中的其他方法，等需要的时候再做实现
}
```
我们的终极版本终于迭代出来了，让我们看一下，这个版本的 DefaultPromise 和之前的有什么不同。
- 首先这时候的 DefaultPromise 已经不再是一个 Runnable 了，所以删去了该类中的 Callable 成员变量和 run 方法。
- 同时添加了新的 setSuccess 方法，setSuccess0 方法和 sync 方法。

接下来，就让我们通过一个小例子最终梳理一下程序的执行流程。
```java
public class NettyTest {

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        //创建一个selector
        Selector selector = Selector.open();
        //创建一个服务端的通道
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        //创建一个promise
        DefaultPromise promise = new DefaultPromise();
        //创建一个runnable，异步任务
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                //将channel注册到selector上,关注接收事件
                serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
                //在这里给promise中的result成员变量赋值
                promise.setSuccess(null);
            }
        };

        //异步执行runnable任务
        Thread thread = new Thread(runnable);
        //启动线程
        thread.start();
        //主线程阻塞，直到promise.setSuccess(null)这行代码执行了才继续向下运行
        promise.sync();
        //服务端channel绑定端口号
        serverSocketChannel.bind(new InetSocketAddress("127.0.0.1", 8080));
    }
}
```
这一次我们让 Promise 成为了 Runnable 任务的一部分，而不是 Runnable 任务本身，这样使用起来是不是就简单多了？

一个 Promise 在两个线程之间传递使用，在异步任务中，服务端 channel 注册到 selector 上之后，紧接着就向 promise 中设定了一个 null 值，

这时 setSuccess(null) 会进一步调用到 setSuccess0 方法中，经过一个三元运算符，null 就会被 SUCCESS 取代，赋值给 promise 中的 result 成员变量，接着会调用 checkNotifyWaiters 方法唤醒阻塞的外部线程。

阻塞在 promise.sync() 处的主线程就被唤醒了，继续向下执行绑定端口号任务。这就是 Promise 方式的线程间通信。

到这里，这节课的核心内容才算是真正结束了。核心内容不算多，但因为是和 Java 的 FutureTask 对比着来学习的，所以学习过程有点漫长。

但我们总算有了个好结果，亲自实现了一个比 FutureTask 更为灵活的 DefaultPromise，抛弃了 FutureTask 中大量的状态转换和阻塞外部线程时需要包装的数据结构，最后甚至抛弃了 DefaultPromise 原本的 Runnable 身份。

**让其成为异步任务的一部分，而不是异步任务本身，而这一实现将异步任务执行完成后的结果（DefaultPromise 的成员变量 result）赋值的权力，交给了用户**。这一点，大家可以再体会体会。

## 引入回调函数
课程到这里确实可以结束了，但是仍然有一个很明显的缺陷，请大家再仔细看看上面的这个例子，会发现服务端 channel 和端口绑定的工作是由主线程来完成的。

但是通过前三节课的学习，我们都知道在 Netty 中，这个工作是要由单线程执行器来执行的。 所以，我们应该再次对上面的测试例子做一番改动。

但是还需要满足一个前提是，让异步线程在执行完服务端 channel 的注册任务后，接着去执行和端口号绑定的任务。我想肯定有人会这么建议，直接把这两个任务写在一个 Runnable 中顺序执行不就完了？就像在主线程中顺序执行这两个任务一样。

但在 Netty 中，服务端 channel 的初始化和注册多路复用器selector是一个庞大的任务，中间会经过一系列复杂的方法来向服务端 channel 中填充一些内容，而绑定端口号的方法也有自己的调用链路，Netty 的作者就将这两个任务分割开了。

**可不管怎么做，由同一个单线程执行器来顺序执行这两个任务的编码思路是无法改变的**，
```java 
SingleThreadEventExecutor.java

    private void doStartThread() {
        //这里的executor是ThreadPerTaskExecutor，runnable -> threadFactory.newThread(command).start()
        //threadFactory中new出来的thread就是单线程线程池中的线程，它会调用nioeventloop中的run方法，无限循环，直到资源被释放
        executor.execute(new Runnable() {
            @Override
            public void run() {
                //Thread.currentThread得到的就是正在执行任务的单线程执行器的线程，这里把它赋值给thread属性十分重要
                //暂时先记住这一点
                thread = Thread.currentThread();
                if (interrupted) {
                    thread.interrupt();
                }
                //线程开始轮询处理IO事件，父类中的关键字this代表的是子类对象，这里调用的是nioeventloop中的run方法
                SingleThreadEventExecutor.this.run();
                logger.info("单线程执行器的线程错误结束了！");
            }
        });
    }
```
成员变量executor来自于NioEventLoop初始化阶段，到目前位置NioEventLoop初始化无论是父组还是子组，都交给了NioEventLoopGroup.java来创建
```java
NioEventLoopGroup.java

    @Override
    protected EventLoop newChild(Executor executor, Object... args) throws Exception {
        EventLoopTaskQueueFactory queueFactory = args.length == 4 ? (EventLoopTaskQueueFactory) args[3] : null;
        return new NioEventLoop(this, executor, (SelectorProvider) args[0],
                ((SelectStrategyFactory) args[1]).newSelectStrategy(), (RejectedExecutionHandler) args[2], queueFactory);
    }
```
与NioEventLoopGroup对应的是执行器组MultithreadEventExecutorGroup.java， 需要注意的是成员变量children[]数组是EventExecutor类型，但为什么能够接收上面的newChild返回的是EventLoop类型呢？答案在netty-04分支中EventLoop接口继承了EventExecutor
```java
protected MultithreadEventExecutorGroup(int nThreads, Executor executor,
                                            EventExecutorChooserFactory chooserFactory, Object... args) {
        if (nThreads <= 0) {
            throw new IllegalArgumentException(String.format("nThreads: %d (expected: > 0)", nThreads));
        }

        if (executor == null) {
            executor = new ThreadPerTaskExecutor(newDefaultThreadFactory());
        }
        //在这里给线程组赋值，如果没有定义线程数，线程数默认就是cpu核数*2
        children = new EventExecutor[nThreads];

        for (int i = 0; i < nThreads; i ++) {
            boolean success = false;
            try {
                //创建每一个线程执行器，这个方法在NioEventLoopGroup中实现。
                children[i] = newChild(executor, args);
                success = true;
            } catch (Exception e) {
                throw new IllegalStateException("failed to create a child event loop", e);
            } finally {
                if (!success) {
                    //如果第一个线程执行器就没创建成功，剩下的方法都不会执行
                    //如果从第二个线程执行器开始，执行器没有创建成功，那么就会关闭之前创建好的线程执行器。
                    for (int j = 0; j < i; j ++) {
                        children[j].shutdownGracefully();
                    }

                    for (int j = 0; j < i; j ++) {
                        EventExecutor e = children[j];
                        try {
                            //判断正在关闭的执行器的状态，如果还没终止，就等待一些时间再终止
                            while (!e.isTerminated()) {
                                e.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
                            }
                        } catch (InterruptedException interrupted) {
                            //给当前线程设置一个中断标志
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }
        //执行器选择器
        chooser = chooserFactory.newChooser(children);
        Set<EventExecutor> childrenSet = new LinkedHashSet<EventExecutor>(children.length);
        Collections.addAll(childrenSet, children);
        readonlyChildren = Collections.unmodifiableSet(childrenSet);
    }
```
总之，由同一个单线程执行器来顺序执行服务端 channel 的初始化和注册多路复用器selector这两个任务的编码思路是无法改变的，每个NioEventLoop本质上是1个ThreadPerTaskExecutor执行器

因此Netty 的作者没有让主线程来顺序执行服务端 channel 的初始化和注册多路复用器selector这两个任务。而是交给ThreadPerTaskExecutor执行器，同时为 Promise 引入了监听器方法，逻辑上分离了这两个任务。

等到 channel 注册到多路复用器上后，会触发函数的回调，我们的单线程执行器就会执行 ChannelFutureListener 这个监听器中的回调方法，将服务端 channel 和端口号绑定。

具体是怎么做的呢？请看下面一段代码（截取自下个分支）
```java
public class ServerBootstrap<C extends Channel> {
    ......

    private ChannelFuture bind(SocketAddress localAddress) {
        //服务端的channel在这里初始化，然后注册到单线程执行器的selector上
        final ChannelFuture regFuture = initAndRegister();
        //得到服务端的channel
        Channel channel = regFuture.channel();
        //要判断future有没有完成
        if (regFuture.isDone()) {
            //完成的情况下，直接开始执行绑定端口号的操作,首先创建一个future
            ChannelPromise promise = new DefaultChannelPromise(channel);
            //执行绑定方法
            doBind(regFuture, channel, localAddress, promise);
            return promise;
        }else {
            //走到这里，说明上面的initAndRegister方法中，服务端的channel还没有完全注册到单线程执行器的selector上
            //此时可以直接则向regFuture添加回调函数，这里有个专门的静态内部类，用来协助判断服务端channel是否注册成功
            //该回调函数会在regFuture完成的状态下被调用，在回调函数中进行服务端channel与端口号的绑定
            final PendingRegistrationPromise promise = new PendingRegistrationPromise(channel);
            regFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    Throwable cause = future.cause();
                    if (cause != null) {
                        promise.setFailure(cause);
                    } else {
                        //走到这里说明服务端channel在注册过程中没有发生异常，已经注册成功，可以开始绑定端口号了
                        promise.registered();
                        doBind(regFuture, channel, localAddress, promise);
                    }
                }
            });
            return promise;
        }
    }

    ......
}
```
代码的具体逻辑我都写在注释中了，非常详细。这里只需要解释一下 ChannelFuture 这个接口。该接口也继承了 Future 接口，其实现类为 DefaultChannelPromise，而 DefaultChannelPromise 继承了 DefaultPromise，所以该接口的实现类的功能几乎都来自 DefaultPromise 类。

还记得我们之前说 DefaultPromise 是整个 Promise 体系最核心的类吗？就体现在此。ChannelFuture 这个接口会在后面章节引入 Channel 之后，一起引入我们的手写 Netty 中。


