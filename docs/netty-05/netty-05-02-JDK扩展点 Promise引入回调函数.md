下面我为大家引入 Promise 的监听器，我们常说的回调函数就是通过监听器实现的。

在我看来，虽然回调函数的应用十分广泛，但其实现非常简单，所以相较于前几节课，这节课的内容也会很简单，只通过一两个小例子就能把知识讲完。
## 回调函数的作用
在正式开始课程之前，我们先来看看什么是回调函数，以及回调函数在程序中的作用。

我最早接触回调函数还是在学习 C 语言时，将一个函数作为参数传递到另一个函数中，以此在程序中达到解耦的目的。

这么说可能有些抽象，下面我将用 Java 为大家写一个简单的小例子。请看下面的代码。
```java
public class Test {

    public static void main(String[] args) {
        int i = operation(1, 2);
        System.out.println(i);
    }
    
    //对两个数做运算的方法
    public static int operation(int a, int b) {
        return a+b;
    }
}
```

在上面的代码中，我定义了一个对两个数做运算的方法，然后在 main 函数中执行。这段代码平平无奇，体现不出什么东西。

但现在，我想提一个新要求，在 operation 方法内，我不希望两个数做加法，而是做减法。这样一来，好像除了改动这个方法，似乎也没有其他可行措施了。

方法数量较少的时候还可以对代码进行改动，但随着我要求增多，比如，我希望两个数做乘法、除法等等，难道每提一个要求，就要改动一次方法？

这未免太麻烦，如果做什么运算能由用户自己定义就好了。这样一来，调用函数的时候我们再确定这两个数究竟做什么运算。就像下面这段代码。
```java
public class Test {

    public static void main(String[] args) {
        int i = operation(1, 2，callback);
        System.out.println(i);
    }

    //我们自己定义的让两个数做加法的方法
     public static int callback(int a, int b) {
        return a+b;
    }
    
    //对两个数做运算的方法，将定义的function当作参数直接传递到该函数中
    public static int operation(int a, int b，Callback callback) {
        return callback(a,b);
    }
}
```

在上面的代码块中，我定义了一个新的 callback 方法，将该方法作为参数传递到 operation 方法内。如果用户可以自行定义让两个数做运算的方法，并且该方法可以作为参数传递到真正被调用函数中，这是不是就意味着真正的目标函数，也就是 operation 函数，和两个数做运算这一任务解耦了？

每当用户需要这两个数做不一样的运算，就不必再改动 operation 方法，只需自己定义一个 callback 方法，传递到目标函数中即可。

如果我们希望这两个数做减法，那就定义一个做减法的 callback 函数，如果有其他需求，那就定义相应的 callback 函数。这体现了回调函数的另一作用：对功能的扩展。

遗憾的是，在 Java 中，方法并不能作为参数，在其他方法内传递。通常情况下，我们可以使用接口达到与之相同的效果，而且要求该接口内部只能定义一个方法。请看下面的代码。

首先定义一个内部只有一个方法的接口：
```java
 interface Function {
    int add(int a, int b);
}
```
```java
public class Test {

    public static void main(String[] args) {
        //创建一个Function对象，实现其中的add方法
        Function f = new Function() {
            @Override
            public int add(int a, int b) {
                return a+b;
            }
        };
        int i = operation(1, 2,f);
        System.out.println(i);
    }

    //对两个数做运算的方法
    public static int operation(int a, int b, Function function) {
        return function.add(a,b);
    }

}
```
上面这个代码就是我们用 Java 的方式实现的回调方法。逻辑简单，就不再赘述了。

解耦 与扩展，是我初次学习 回调函数 时，对其总结出来的功能。 但随着学习的深入，掌握的框架和读过的代码越来越多，我发现回调函数的功能不仅仅是这样。

可以说，回调函数的出现大大方便了我们程序的编写。在回调函数出现之前，程序执行是严格按照代码的顺序自上而下执行的，但回调函数出现之后，程序运行的顺序就可能被特定的事件打断，在某一时刻执行回调函数。

比如在 Netty 中，服务端的 channel 要去注册 selector 才能绑定端口号。但我们完全可以事先创建一个监听器对象，并以关闭服务端 channel 为核心逻辑实现其中的方法，然后调用 Promise 的 addListener 方法，将监听器传递到 DefaultPromise 对象中。

等到程序执行完注册 channel 到多路复用器上后，单线程执行器就会回调监听器对象中的方法来绑定端口。并且判断如果 channel 注册多路复用器失败了，就会执行关闭服务端 channel 的方法。在这个例子中，监听器对象内的方法，就要被程序回调的方法。

或者是在 RPC 远程调用框架内，我们也可以事先创建好一个监听器对象，并以重新发送消息为核心逻辑实现其中的方法。当消息发送后，该监听器内的方法将会被回调。如果消息发送失败，就执行方法内重发消息的逻辑。

可以看到，回调函数是否被回调，或者说我们的程序是否会触发函数的回调，终究还是取决于某些特定的情况是否出现。所以我们称回调函数是被事件驱动的。

在另一个著名框架 Spring 中，ApplicationListener 监听器内的方法是否被回调，取决于某些事件是否被发布。简单来说，就是在程序触发了某些事件之后，回调函数就会被执行。

当然，大家可能也都想到了，我以 Netty 和 Spring 中的回调函数来举例，是因为不止这两个框架，大多数框架都会将一些回调函数放在一个监听器对象内，用监听器对象来包装回调函数。 而这种被监听器包装的回调函数通常又会配合观察者这种设计模式来使用。所以，接下来，我将用观察者模式为大家实现一个简单的回调函数的例子。

## 观察者模式中的回调函数
直接讲解观察者模式的概念，多少有点不直观。在我的理解中，编程中的很多概念实际上是对代码的总结，而不是在代码展示之前，对代码的整体描述。

换言之，我们首先应该学习最直观的代码，从代码中体会观察者模式，所以我为大家准备了以下几段代码。首先，定义一个观察者的通用接口。
```java
/**
 * @Author: PP-jessica
 * @Description:这是一个观察者
 */
public interface Listener {

    void  doSomething();

}
```
接着是两个具体的观察者，一个父亲，一个母亲。
```java
/**
 * @Author: PP-jessica
 * @Description:这是一个具体的观察者
 */
public class Father implements Listener{
    @Override
    public void doSomething() {
        System.out.println("观察者--父亲：给孩子买点橘子，留给孩子一个苍老的背影");
    }
}
```
```java
/**
 * @Author: PP-jessica
 * @Description:这是一个具体的观察者
 */
public class Mother implements Listener{
    @Override
    public void doSomething() {
        System.out.println("观察者--母亲：对坐在车里的孩子挥手，祝他一路顺风");
    }
}
```
接下来是一个被观察者。
```java
/**
 * @Author: PP-jessica
 * @Description:这是一个被观察者
 */
public class YoungPeople {

    /**
     * @Author: PP-jessica
     * @Description:这个集合用来存储所有的观察者
     */
    private final ArrayList<Listener> listeners = new ArrayList<Listener>();


    /**
     * @Author: PP-jessica
     * @Description:把观察者添加到被观察者的集合中
     */
    public YoungPeople addListener(Listener listener) {
        listeners.add(listener);
        return this;
    }


    /**
     * @Author: PP-jessica
     * @Description:去工作的方法
     */
    public void toWork() {
        System.out.println("被观察者--孩子：假期结束，孩子要返程上班了");
        for (Listener listener : listeners) {
            listener.doSomething();
        }
    }
}
```
最后是测试类。
```java
public class Test {

    public static void main(String[] args) {
        //创建两个观察者
        Father father = new Father();
        Mother mother = new Mother();

        //创建一个被观察者
        YoungPeople youngPeople = new YoungPeople();

        //观察者开始观察被观察者
        youngPeople.addListener(father).addListener(mother);

        //被观察者开始要去上班了
        youngPeople.toWork();
    }
}
```
执行结果。
```java
被观察者--孩子：假期结束，孩子要返程上班了
观察者--父亲：给孩子买点橘子，留给孩子一个苍老的背影
观察者--母亲：对坐在车里的孩子挥手，祝他一路顺风
```

在这个例子中，父亲和母亲作为观察者，年轻人作为被观察者。当父亲和母亲观察到孩子要返程工作了，就会做出相应的动作。而这两个观察者之所以能随着被观察者的改变而改变，是因为被观察者内部有一个 List 集合持有了这两个观察者对象。在某个特定时刻，这些观察者对象内的方法都被回调了，这就是回调函数在观察者模式中的体现。

稍微总结一下，我们可以这么说：当某个对象和其他多个对象存在一对多的关系时，只要该对象的状态发生一点变化，或者是做出某种行动，其他对象也会跟着 作出 相应行动。 这就是观察者模式。

## 完善 DefaultPromise, 引入监听器，实现回调机制
如果大家都明白上面这个小例子了，再来学习 Netty 就会简单很多。下面就让我们看看，观察者模式在 Netty 中是怎么运用的。

在上面这个小例子中，大家可以看到回调函数 doSomething 实际上是被一个监听器包装了，所以在我们的 Netty 中，自然也要采用这种编码方式,首先定义一个监听器。
```java
/**
 * @Author: PP-jessica
 * @Description:通用监听器的接口
 */
public interface GenericListener<P extends Promise<?>> {

    void operationComplete(P promise) throws Exception;
}
```
接着在我们上节课实现的 DefaultPromise 的基础上，增添一些新的方法和成员变量。
```java
public class DefaultPromise<V> implements Promise<V> {
    //执行后得到的结果要赋值给该属性
    private volatile Object result;
    //这个成员变量的作用很简单，当有一个外部线程在await方法中阻塞了，该属性就加1，每当一个外部
    //线程被唤醒了，该属性就减1.简单来说，就是用来记录阻塞的外部线程数量的
    //在我们手写的代码和源码中，这个成员变量是Short类型的，限制阻塞线程的数量，如果阻塞的
    //线程太多就报错，这里我们只做简单实现，具体逻辑可以从我们手写的代码中继续学习
    private int waiters;

    /该属性是为了给result赋值，前提是promise的返回类型为void，
    //这时候把该值赋给result，如果有用户定义的返回值，那么就使用用户
    //定义的返回值
    private static final Object SUCCESS = new Object();

    //新添加进来的属性
    //这就是观察者模式中的监听器集合，回调函数就定义在监听器中
    private List<GenericListener> listeners = new ArrayList();

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
        //然后执行监听器的回调方法
        notifyListeners();
    }

    /**
     * @Author: PP-jessica
     * @Description:通知所有的监听器开始执行回调方法
     */
    private void notifyListeners() {
       for (GenericListener listener : listeners) {
            listener.operationComplete(this);
        }
    }

    //添加监听器的方法
    public Promise<V> addListener(GenericListener<? extends Promise<? super V>> listener) {
        synchronized (this) {
            //添加监听器
           listeners.add(listener);
        }
        //判断任务是否完成，实际上就是检查result是否被赋值了
        if (isDone()) {
            //唤醒监听器，让监听器去执行
            notifyListeners();
        }
        //最后返回当前对象
        return this;
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
                //可能是虚假唤醒
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
以上就是我们重构之后的 DefaultPromise 类，其实改动也没有很大，仅仅是按照之前那个观察者模式
- 向 DefaultPromise 类中添加了一个 listeners 集合，用于存储所有的监听器对象；
- 添加了 addListener 方法，用来把监听器对象添加到集合中，
- 添加了 notifyListeners 方法则用来在 result 成员变量被赋值后回调监听器中的方法。

现在，我们可以再把上节课的一个测试类搬过来，稍微改动，展示一下 Netty 中监听器内方法回调的流程。
```java
public class NettyTest {

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        //创建一个selector
        Selector selector = Selector.open();
        //创建一个服务端的通道
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        //创建一个promise
        DefaultPromise promise = new DefaultPromise();
        //向Promise中添加一个监听器
        promise.addListener(
                //创建一个监听器对象
                new ChannelListener() {
                    @Override
                    public void operationComplete(Promise promise) throws Exception {
                        //服务端channel绑定端口号
                        serverSocketChannel.bind(new InetSocketAddress("127.0.0.1", 8080));
                    }
                });
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
        //主线程阻塞就可以注释掉了，因为单线程执行器执行完了注册方法后，会通过回调监听器的方法
        //来将服务端channel和端口号绑定
        //promise.sync();

    }
}
```
现在，这个测试类的核心逻辑就全在 Promise 中了。当我们创建的线程执行了服务端 channel 注册多路复用器的代码后，会执行 promise.setSuccess(null) 这行代码，这行代码内部会一路调用到 notifyListeners 方法内，该方法的作用就是遍历 DefaultPromise 类中成员变量 listeners 集合，回调集合中每一个监听器对象的方法。而我们在代码的第 11 行，就向 Promise 中添加了一个监听器，其内部实现的方法就是将服务端 channel 和端口号绑定。这就是观察者模式在 Netty 中的体现，当然，其本质仍然是回调函数。

在这里我想强调一句，**在Netty这个例子中，最终的回调函数 一定是被异步线程Thread来调用的。主线程的作用只是把监听器添加到 Promise**

**我们也应该承认，在其他一些框架中，回调函数也是由异步线程调用的，比如一些 RPC 框架**。比如naocs源码里的requestfuture也是这样的思路，服务端接受响应的线程调用setResponse()方法解除get()方法的阻塞，同时执行注册的回调，知识相通了

但是，大家一定要清楚，回调并不意味着就是异步，我们最开始的妈妈送孩子那个观察者模式不就是很明确同步的例子吗？那个例子并没有出现任何一个异步线程。所以，回调函数并不一定要异步调用，它只是程序的一种执行方式，让程序更灵活的执行方式。

最后，我希望大家能够明白，回调函数本身十分简单，所以当我们亲自去创造一个轮子时，难点并不是扩展本身，而是在哪里做扩展，为什么要在这里做扩展。现有的框架当然可以用来参考，但千万别让它限制了我们天马行空的想象力。具体怎么扩展，在哪里扩展，大家可以尽情地想象，然后实现，我想大家会感到其乐无穷的。