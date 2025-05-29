这节课我们在上节课的基础上，引入 Unsafe 这个体系。提到 Unsafe，可能很多人都不陌生，在 Java 中就有一个 Unsafe 类，翻译过来就是不安全的类。 而 Netty 中也有这样一个类。

所以，这节课我们会先简单回顾一下 Java 中的 Unsafe 类，然后再引入 Netty 的 Unsafe 接口，对比一下二者的不同。当然，我们这节课完善的 Unsafe 体系，在内容上还很简单，像写缓冲队列，内存的动态分配都和 Unsafe 有关，这节课我们不会讲到这些内容，但是在后面的课程中会逐渐完善。

## Java 的 Unsafe 类

一般来说，我们 Java 程序员在工作中很少直接用到 Unsafe 类的功能，但是间接使用 Unsafe 功能的情况多不胜数。 最直接的例子，在并发编程领域的各种原子类，它们具备“原子”的功能，就是内部持有的 Unsafe 类为它们提供的。请看下面一段代码。
```java
public class AtomicInteger extends Number implements java.io.Serializable {
    private static final long serialVersionUID = 6214790243416807050L;
     //unsafe对象被赋值
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    //value这个属性在对象中的内存偏移量
    private static final long valueOffset;

    static {
        try {
            //得到内存偏移量
            valueOffset = unsafe.objectFieldOffset
                (AtomicInteger.class.getDeclaredField("value"));
        } catch (Exception ex) { throw new Error(ex); }
    }
     //我们存放在原子类中的value
    private volatile int value;

    
    //其他内容省略
    .......


    //直接通过内存地址改变value赋值
    public final int getAndSet(int newValue) {
        return unsafe.getAndSetInt(this, valueOffset, newValue);
    }

    //提供了原语比较，执行过程中不可以被打断
    public final boolean compareAndSet(int expect, int update) {
        return unsafe.compareAndSwapInt(this, valueOffset, expect, update);
    }
    
}
```
Unsafe.java
```java
    private static final Unsafe theUnsafe = new Unsafe();

    //该方法实际上在Unsafe类中
    @CallerSensitive
    public static Unsafe getUnsafe() {
        Class<?> caller = Reflection.getCallerClass();
        if (!VM.isSystemDomainLoader(caller.getClassLoader()))
            throw new SecurityException("Unsafe");
        return theUnsafe;
    }
```

在 AtomicInteger 这个类中，大家会看到它调用了Unsafe类中的 getUnsafe 方法得到了 unsafe 对象，然后赋值给 AtomicInteger 类中的 unsafe 成员变量。


这样，AtomicInteger 原子类就可以通过 unsafe 对象使用 Unsafe 类中的功能了。

因为AtomicInteger 一开始就在静态代码块中得到了 value 属性的内存偏移量，所以在 getAndSet 和 compareAndSet 方法内，unsafe 对象就可以直接通过内存地址对 value 的值做一些改变了。

如果大家已经认真学习了我们之前手写的 Netty 代码，肯定也会在那些代码中见到一些对象使用了 Unsafe 类的功能。比如我们的单线程执行器，就通过原子类AtomicIntegerFieldUpdater使用了 Unsafe 类的功能。
```java
public abstract class SingleThreadEventExecutor implements EventExecutor {

    private static final Logger logger = LoggerFactory.getLogger(SingleThreadEventExecutor.class);
    //执行器的初始状态，未启动
    private static final int ST_NOT_STARTED = 1;

    //执行器启动后的状态
    private static final int ST_STARTED = 2;

    private volatile int state = ST_NOT_STARTED;
    //执行器的状态更新器,也是一个原子类，通过cas来改变执行器的状态值
    private static final AtomicIntegerFieldUpdater<SingleThreadEventExecutor> STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(SingleThreadEventExecutor.class, "state");



    //其他的省略
        ......
}
```

可以看到，在单线程执行器中，同样是使用了一个原子更新类来创建 state 的原子状态更新器。

我们继续跟进，来到 AtomicIntegerFieldUpdater 内部看一看，就会发现，它的内部工作原理，其实是和 AtomicInteger 是一样的，都是持有了 Unsafe 类的对象，通过 Unsafe 类的方法来完成工作的。
```java
public abstract class AtomicIntegerFieldUpdater<T> {


    //受保护的构造器
    protected AtomicIntegerFieldUpdater() {
    }


    @CallerSensitive
    public static <U> AtomicIntegerFieldUpdater<U> newUpdater(Class<U> tclass,
                                                              String fieldName) {
        return new AtomicIntegerFieldUpdaterImpl<U>
                (tclass, fieldName, Reflection.getCallerClass());
    }


    private static final class AtomicIntegerFieldUpdaterImpl<T>
            extends AtomicIntegerFieldUpdater<T> {
        //unsafe对象赋值
        private static final sun.misc.Unsafe U = sun.misc.Unsafe.getUnsafe();
        //state属性的内存偏移量
        private final long offset;
        //目标对象的类型，其实就是SingleThreadEventExecutor单线程执行器，因为要得到的
        //是state在SingleThreadEventExecutor中的内存偏移量
        private final Class<?> cclass;
        //这里是为了获得调用原子更新器方法的对象，详细知识可以从
        //Reflection.getCallerClass()方法的作用学习
        private final Class<T> tclass;

        AtomicIntegerFieldUpdaterImpl(final Class<T> tclass,
                                      final String fieldName,
                                      final Class<?> caller) {
            //属性
            final Field field;
            //权限修饰符
            final int modifiers;
            try {
                //得到具体的属性
                field = AccessController.doPrivileged(
                        new PrivilegedExceptionAction<Field>() {
                            public Field run() throws NoSuchFieldException {
                                return tclass.getDeclaredField(fieldName);
                            }
                        });
                //确保可访问的权限
                modifiers = field.getModifiers();
                sun.reflect.misc.ReflectUtil.ensureMemberAccess(
                        caller, tclass, null, modifiers);
                ClassLoader cl = tclass.getClassLoader();
                ClassLoader ccl = caller.getClassLoader();
                if ((ccl != null) && (ccl != cl) &&
                        ((cl == null) || !isAncestor(cl, ccl))) {
                    sun.reflect.misc.ReflectUtil.checkPackageAccess(tclass);
                }
            } catch (PrivilegedActionException pae) {
                throw new RuntimeException(pae.getException());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            //属性是int类型的
            if (field.getType() != int.class)
                throw new IllegalArgumentException("Must be integer type");
            //属性要被volatile修饰
            if (!Modifier.isVolatile(modifiers))
                throw new IllegalArgumentException("Must be volatile type");
                
                ......
            //得到内存偏移量
            this.offset = U.objectFieldOffset(field);
        }
    }

    //其他内容省略
    ......
}
```
可以看到，因为 AtomicIntegerFieldUpdater 类中的无参构造器被 protected 修饰，所以我们无法通过构造函数创建 AtomicIntegerFieldUpdater 的实例引用，

只能调用的是该类中的 newUpdater 方法，在该方法内会返回一个 AtomicIntegerFieldUpdater 的子类 AtomicIntegerFieldUpdaterImpl 对象，实际上为单线程执行器工作的就是这个子类对象。

当然，工作的具体方法仍然是 Unsafe 类提供的，前面我们都已经看过了，代码中也添加了注释，所以就不再细说了。

除了我们上面列出的这两个例子，Java 的 Unsafe 类还为用户提供了调度线程的方法。比如 LockSupport，它内部就持有了 Unsafe 类的对象，当我们调用 LockSupport 的 park 和 unpark 方法时，最终会调用到 Unsafe 类中的方法。
```java
    public native void unpark(Object thread);

    public native void park(boolean isAbsolute, long time);
```

除此之外，Unsafe 类还为我们提供了和 Volatile 相关的读写方法。
```java
    public native Object getObjectVolatile(Object o, long offset);

    public native void    putObjectVolatile(Object o, long offset, Object x);

    public native int     getIntVolatile(Object o, long offset);

    public native void    putIntVolatile(Object o, long offset, int x);

    public native boolean getBooleanVolatile(Object o, long offset);

    public native void    putBooleanVolatile(Object o, long offset, boolean x);

    public native byte    getByteVolatile(Object o, long offset);

        //省略其他方法
        ......
```

还有设置内存屏障的方法。
```java
    //该屏障之前读操作已完成
    public native void loadFence();

   //该屏障之前写操作完成
    public native void storeFence();

    //读写操作都完成
    public native void fullFence();
```

Unsafe 类为用户提供的最重要的一个功能，也是最危险的一个功能，就是可以操作内存了。比如下面的一些代码。
```java
   //分配一块内存
   public native long allocateMemory(long bytes);

    //重新分配内存
    public native long reallocateMemory(long address, long bytes);

     //设置内存内容
    public native void setMemory(Object o, long offset, long bytes, byte value);

    public void setMemory(long address, long bytes, byte value) {
        setMemory(null, address, bytes, value);
    }

     //复制内存内容
    public native void copyMemory(Object srcBase, long srcOffset,
                                  Object destBase, long destOffset,
                                  long bytes);
        
    public void copyMemory(long srcAddress, long destAddress, long bytes) {
        copyMemory(null, srcAddress, null, destAddress, bytes);
    }

    //释放内存
    public native void freeMemory(long address);
```
操作内存的方法等我们讲到 ByteBuf 和内存池的时候，我们会讲到堆内存和直接内存的区别，就会用到 Unsafe 类中的方法为我们分配内存。这本来是 C 语言中的方法和功能，如今通过 Java 的 Unsafe 类也可以实现了。

那么，Netty 中也有一个 Unsafe 接口，这个接口也有多个实现类，Netty 作者这么做的用意究竟何在呢？

难道也是跟 Java 一样，在 Netty 中也有一些方法和底层有关，不太适合对外暴露，所以都放在 Unsafe 接口的实现类中了？情况可能并非大家想象的这样。

## 引入 Netty 的 Unsafe 接口
上节课我们引入了 Channel 接口，并且以该接口为起点，将我们 NIO 模式下能够用到的 channel 都引入进来了，可以说 channel 体系已经很完整了。

如果我们想用服务端的 channel 去绑定端口号，就可以直接创建一个 NioServerSocketChannel，然后执行它的 bind 方法就行了，该方法内部会经过一系列调用，最终仍然是在该类自身中的 doBind 方法内，执行了真正的服务端绑定端口号的方法。

客户端连接服务端的方法和接收消息的方法我们也做了实现，唯独剩下发送消息的方法我们还没做实现，所以，现在我就在该接口中再定义一个 write 方法。

```java
public interface Channel {

    void bind(SocketAddress localAddress, ChannelPromise promise);

  
    void connect(SocketAddress remoteAddress, final SocketAddress localAddress,ChannelPromise promise);
     //该方法只在本测试例子中使用，手写Netty和源码中，write方法并非这样定义的
    void write(Object msg)
    //省略了很多方法
    ......
}
```
现在，我们在 Channel 接口中新添加了一个 write 方法，当然，我们的例子比较简单，所以定义的方法越简单越好，方便大家理解。既然接口中有了新的方法，那实现类就该实现这个方法。我们都知道，发送消息其实是 NioSocketChannel 的事情，所以，write 方法自然应该让客户端的 channel 去真正实现，至于服务端的 channel，做一个空实现即可。

但我们为了简化步骤，就不在这里再次引入 NioSocketChannel 的各种抽象父类了，而是让 NioSocketChannel 直接实现 write 方法。
```java
public class NioSocketChannel implements Channel{

    //这里我们只做write方法的实现，其他方法暂时都略去
     @Override
    public void write(Object msg){
        //真正发送数据的时候到了，要用java原生的socketchannel
        //javaChannel()可以得到Java原生channel
        SocketChannel socketChannel = javaChannel();
        //发送数据
        socketChannel.write(msg);
    }

    //其他方法省略
    ......
}
```

程序总是随着不同的需求被重构，现在忽然有了一个需求，就是检测我们发送的消息中是否包含一些敏感信息，比如辱骂他人的话，或者是恶意造谣别人的话，如果有这些敏感信息，我们的程序就会发出警报，并且清空这些消息。 这个需求提得似乎也没什么难度，所以我很快就想到了一个解决方法。

我打算定义一个新的类，然后在这个类中定义一个方法，这个方法专门检测发送的消息中是否包含敏感数据，如果包含，就直接打印拒绝发送消息的警告。

仔细想想，这种检测手段是不是类似于我们进高铁站和地铁站时，要经过安检。我们会把自己的行李放到传送带上，进入一条通道，经过机器扫描。

这样类比一下，我们发送的消息实际上也会在发送之前进入一条管道，经过检查后才被允许发送出去。所以，我就给这个新的类起名为 pipeline。
```java
public class Pipeline{


    //定义检测消息的方法
    public static void checkMsg(Object msg){
        if("骂人的话、谣言".equals(msg)){
            System.out.println("包含敏感信息，消息不能发送！");
            //消息清空
            msg = null;
        }
    }
    
}
```

NioSocketChannel 要做的一点改动。
```java
public class NioSocketChannel implements Channel{

    //这里我们只做write方法的实现，其他方法暂时都略去
     @Override
    public void write(Object msg){
        Pipeline.checkMsg(msg);
        //真正发送数据的时候到了，要用java原生的socketchannel
        //javaChannel()可以得到Java原生channel
        SocketChannel socketChannel = javaChannel();
        //发送数据
        socketChannel.write(msg);
    }

    //其他方法省略
    ......
}
```

消息少的时候，一条一条检查还可以，但是消息一多，几百万条消息都要一条一条地检查，程序的效率就大大降低了。

所以这时候就又有了一个需求。当消息特别多的时候，就不检查消息是否包含敏感信息了，直接发送出去就行，反正冤有头债有主，哪个人发的到时候直接找哪个人就行。消息少的时候，我们再对消息进行敏感词汇检查。

这样一来，我们的程序就又要做一些改动，最原始的 write 也应该保留下来满足老的检查逻辑。为了给这新的发送方法（不检查）做一点区别：
- 我们就称检查敏感词汇的方法为安全发送消息的方法
- 而不检查敏感词汇的方法为不安全的发送消息方法。

而且为了进一步将两个发送消息的方法区分得更明显，我想从类的结构上做一点改动。反正发送消息都是 channel 来做，所以，我就在 Channel 接口中，定义了一个新的内部接口 Unsafe，然后在 NioSocketChannel 中定义了一个内部类实现 Unsafe 接口。
```java
public interface Channel {
    //这次我们只保留这一个方法。其他方法都略去
    //该方法只在本测试例子中使用，手写Netty和源码中，write方法并非这样定义的
    void write(Object msg)
    //省略了很多方法
    ......

    //内部接口Unsafe
    interface Unsafe {
        //发送消息的方法
         void write(Object msg);
    }
}
```
接下来是 Channel 接口的实现类 NioSocketChannel。
```java
public class NioSocketChannel implements Channel{

    //这里有一个比较大的改动，就是把Pipeline类定义成该类的一个成员变量
    Pipeline pipeline = new Pipeline(getUnsafe());

    //返回一个Unsafe类的对象
    NioUnsafe getUnsafe(){
        return new NioUnsafe();
    }

    //这里我们只做write方法的实现，其他方法暂时都略去
     @Override
    public void write(Object msg){
        //经过管道检查消息是否包含敏感词汇
        Pipeline.checkMsg(msg);
    }
    
      //新增加的方法，真正发送客户端消息的方法
     protected void doWrite(Object msg){
        //真正发送数据的时候到了，要用java原生的socketchannel
        //javaChannel()可以得到Java原生channel
        SocketChannel socketChannel = javaChannel();
        //发送数据
        socketChannel.write(msg);
     }

     //Unsafe接口的实现类
    protected  class NioUnsafe implements Unsafe {
        //该方法是Unsafe接口中方法的实现
             @Override
            public void write(Object msg){
                doWrite(msg);
            }
        
    }
    
    //其他方法省略
    ......
}
```
改动之后的 Pipeline 类。
```java
public class Pipeline{

    private NioSocketChannel.NioUnsafe unsafe;

    public Pipeline(){
        
    }

    public Pipeline(NioSocketChannel.NioUnsafe unsafe){

        this.unsafe = unsafe;
        
    }
    //定义检测消息的方法
    public static void checkMsg(Object msg){
        if("骂人的话、谣言".equals(msg)){
            System.out.println("包含敏感信息，消息不能发送！");
            //消息置为*
            msg = ****;
            unsafe.write(msg);
        }
    }
    
}
```
最后是测试类。
```java
public class Test {
    public static void main(String[] args) {
       //创建一个客户端channel，用来发送消息
        NioSocketChannel channel = new NioSocketChannel();
        //获得客户端channel中的内部类
         NioSocketChannel.NioUnsafe unsafe = channel.getUnsafe();
        //创建一条要发送的消息
        Object msg = new Object();
        //直接用客户端channel发送消息
         channel.write(msg);
        //直接用unsafe对象发送消息
        unsafe.write(msg);
    }
}
```

好了，现在先来分析上面代码块中的第 10 行，channel.write(msg);直接用客户端 channel 发送消息的情况。当我们调用这行代码的时候：
- 逻辑就会来到客户端 channel 自身之中，会在它的 write 方法内执行 Pipeline.checkMsg( msg ) 这行代码，检查要被发送的 msg 中是否包含敏感信息。
- 而这行Pipeline.checkMsg( msg )代码的逻辑又会来到 Pipeline 类中，在该类的 checkMsg 方法内，会先检查 msg 是否有敏感信息，然后调用 NioSocketChannel.NioUnsafe 内的 write 方法，
- 所以，我们的逻辑又会回到 NioSocketChannel 自身的内部类 NioUnsafe 中。
- 而在这个内部实现类NioUnsafe的 write 方法内，就会执行NioSocketChannel真正的 doWrite 方法，真正把消息发送出去。

而当我们执行上面代码块的第 12 行代码时，逻辑就会直接来到 NioSocketChannel 自身的内部类 NioUnsafe 中，执行内部实现类的 write 方法，进而执行到 doWrite 方法，把 msg 发送出去。

**同理的在服务端的 NioServerSocketChannel 中 doBind 方法也是被 protected 修饰，所以无法直接调用，只能调用 bind 方法，而执行 bind 方法就会走一遍 ChannelPipeline，然后才经由 Unsafe 接口的实现类调用到服务端 channel 自身中的 doBInd 方法。如果直接由 Unsafe 接口的实现类执行绑定端口号的操作，就不会经过 ChannelPipeline，就会被视为不安全的操作。其他方法的流程也是如此，就不再详细展开了。**

所以现在你明白了。在 Netty 中，Unsafe 接口的存在并不是要将一些底层方法跟用户隔离开，实际上也根本没什么底层方法。

Netty 之所以这么做，就是希望发送的消息能走一遍 ChannelPipeline，被其中的各种 handler 处理器处理一下。

最典型的就是减少 ByteBuf 的引用计数，防止内存泄漏，这个大家应该都听说过，后面我们会亲自实现的。

如果不走一遍 ChannelPipeline，那操作的数据流程就会被视为不安全的。

并且不只是 writeAndFlush 方法，bind、read、connect 等多种方法都像我们上面那个小例子那样，都会经过 ChannelPipeline，然后在 ChannelPipeline 的处理器中调用 unsafe 对象的方法，在 unsafe 对象的方法内，就可以进一步执行到 do 开头的各种真正干活的方法中了。

## 源码内部的构造
现在我无法给大家举更多的例子，是因为我们的这节课程安排在这里其实还是有些跳跃的。实际上，我们在引入了 ChannelPipeline 体系后再来讲解 Unsafe 效果就会好很多，上面这个小例子中的 Pipeline 类，其实到后面的课程中就会被替换成 ChannelPipeline 接口的实现类。

但如果我先讲 ChannelPipeline，到后面我们手写的 Netty 代码重构起来就会很麻烦，所以我就先把 Unsafe 接口引入了。

这节课的核心内容就到此为止了，但是我们上面的例子十分简短，而且只模拟了客户端发送消息的方法。

但是在网络编程中有客户端和服务端两种 channel，所以放到 Netty 中，Unsafe 接口的众多实现类，也应该根据 channel 的类型做出具体的区分。

因此，下面我会贴出来一些代码，带大家简单看一看 Unsafe 接口实现类的体系。然后，大家就可以去学习手写代码和源码了。

既然 Unsafe 接口要定义成 Channel 接口的内部接口，
```java
public interface Channel extends  ChannelOutboundInvoker{//大家可以看到这里忽然多了 ChannelOutboundInvoker 接口，这个接口就定义了 channel 的出站方法，具体怎么用，等后面我们讲到 ChannelPipeline 时就清楚了，现在先混个眼熟。

    interface Unsafe {

        
    }
}
```
所以 AbstractChannel 类中也会有一个 Unsafe 接口的内部实现类 AbstractUnsafe。就像下面这样。具体的方法和属性我就不列出来了，我们只看最基本的结构关系。
```java
public abstract class AbstractChannel implements Channel{

    protected abstract class AbstractUnsafe implements Unsafe {
        
    }
    
}
```
接下来就是 AbstractChannel 的子类，专门负责 NIO 模式消息处理的抽象类 AbstractNioChannel。
```java
public abstract class AbstractNioChannel extends AbstractChannel {

    //在该类中又定义了一个内部接口
    public interface NioUnsafe extends Unsafe {

     
    }

    //继承AbstractUnsafe的基础上又实现了该类中NioUnsafe接口
    protected abstract class AbstractNioUnsafe extends AbstractUnsafe implements NioUnsafe {

        
    }
    
}
```
接下来就是负责处理客户端的抽象类 AbstractNioByteChannel。
```java
public abstract class AbstractNioByteChannel extends AbstractNioChannel{

    
    protected class NioByteUnsafe extends AbstractNioUnsafe {
       
    }
}
```
然后就是负责处理服务端的抽象类 AbstractNioMessageChannel。
```java
public abstract class AbstractNioMessageChannel extends AbstractNioChannel {

    private final class NioMessageUnsafe extends AbstractNioUnsafe {
        
    }
}
```

最后还想再啰嗦下，在 NioServerSocketChannel 中 doBind 方法被 protected 修饰，所以无法直接调用，只能调用 bind 方法，而执行 bind 方法就会走一遍 ChannelPipeline，然后才经由 Unsafe 接口的实现类调用到服务端 channel 自身中的 doBInd 方法。如果直接由 Unsafe 接口的实现类执行绑定端口号的操作，就不会经过 ChannelPipeline，就会被视为不安全的操作。其他方法的流程也是如此，就不再详细展开了。

![protected-doBind.png](protected-doBind.png)

这节课的重点只有一个，就是明白 Netty 中 Unsafe 接口和其实现类的设计理念。明白设计这样一个东西只是为了让执行的方法经过 ChannelPipeline，和 Java 中 Unsafe 类的设计理念并不相同。

