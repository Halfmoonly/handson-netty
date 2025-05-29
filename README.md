# handson-netty
想要深入研究分布式服务，我认为有两个前提：
- netty
- raft

本仓库旨在手写netty框架，采用多分支开发，每个分支都是可运行的程度：
- netty-01：带你回顾NIO的经典编程范式，举例如下：
  - 客户端socketChannel感兴趣的有connect事件和read事件，
  - 服务端serverSocketChannel感兴趣的有accept事件和read事件，
  - 服务端会在accept事件中回写客户端连接成功通知，并注册客户端channel给到自己的selector，并给客户端的channel设置可读事件方便后续监听读事件
- netty-02：引入单线程池将服务端的accept事件和read事件分离，利用阻塞队列异步注册read事件，后期由此单线程池负责事件轮询消费事件，标准的Reactor线程模型
- netty-03：采用继承的方式重构了SingleThreadEventExecutor(顶层抽象类)，将NIO的事件轮询处理丢给NioEventLoop(核心实现)，将NIO的事件注册丢给SingleThreadEventLoop(中间层抽象类)，达到职责单一的目的
- netty-04：原有的三层继承关系NioEventLoop->SingleThreadEventLoop->SingleThreadEventExecutor的基础上(NSS)，逐步构建出netty的核心框架结构
  - **NioEventLoop成组**：定义两个接口，EventLoopGroup接口组合了EventLoop接口，并且这两个接口还是继承关系EventLoop->EventLoopGroup
  - **SingleThreadEventExecutor成组**：定义两个接口，EventExecutorGroup执行器组接口组合了EventLoop接口，并且这两个接口还是继承关系EventExecutor->EventExecutorGroup
  - **为了使EventLoop接口拥有EventExecutor接口的能力**，这两个接口也是继承关系EventLoop->EventExecutor
  - **为了使EventLoopGroup接口拥有EventExecutorGroup接口的能力**，这两个接口也是继承关系EventLoopGroup->EventExecutorGroup，因此与NSS对称，成组这边形成了新的三层继承关系(NMM)是NioEventLoopGroup->MultithreadEventLoopGroup->MultithreadEventExecutorGroup
- netty-05：Netty的异步回调，基于Future接口实现自己的Promise实现线程间通信，Promise和Future的区别如下：
  - 弥补了JDK.Future接口的缺陷，实现了事件监听器，同时监听器回调也是由异步线程(Netty单线程执行器)执行的
  - 与Future不同，Promise并不作为具体的任务本身因为Netty有专门的单线程执行器，Promise它只用于获取结果和执行监听器，所以它不用继承Runnable接口，而是把 DefaultPromise 类中 result 成员变量结果的赋值权力交给用户
  - Future用的JUC中的LockSupport实现的内外部线程同步，Promise用的JVM的wait/notifyAll实现的内外部线程同步
- netty-06：SingleThreadEventLoop通过将channel作为附件的方式来注册客户端channel和服务端channel，方便了日后NioEventLoop通过key直接获得当前channel来读写操作，NioEventLoop不必持有成员变量serverSocketChannel和socketChannel。为此正式引入Channel接口体系：
  - 顶级抽象类AbstractChannel实现接口方法register(EventLoop eventLoop)：一个 selector 可以注册多个 channel，因为服务端ServerSocketChannel会监听多个客户端SocketChannel。
  - 顶级抽象类AbstractChannel实现接口方法EventLoop eventLoop()：反过来一个channel只归属于一个selector，一个 selector 拥有一个单线程执行器executor ，无论是服务端还是客户端单线程执行器启动的那一刻，就会在 run 方法中无限循环，在 run 方法内，要不停判断该单线程执行器持有的 selector 是否有 IO 事件到来，如果有就执行 IO 事件。
  - **客户端NioSocketChannel和服务端NioServerSocketChannel的read行为不同**，因此客户端NioSocketChannel需要继承抽象类AbstractNioByteChannel实现特定的doReadBytes方法，服务端NioServerSocketChannel需要继承抽象类AbstractNioMessageChannelread实现特定的doReadMessages方法
  - 抽象类AbstractNioByteChannel和AbstractNioMessageChannel均继承自AbstractNioChannel，AbstractNioChannel继承自顶级抽象类AbstractChannel
  - 最后还引入了channel的反射创建工厂ReflectiveChannelFactory给启动类使用
- 更多分支，持续更新中

main分支涵盖以上所有分支功能，全量文档见：[docs](docs)
