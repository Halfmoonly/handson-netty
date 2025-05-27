# handson-netty
想要深入研究分布式服务，我认为有两个前提：
- netty
- raft

本仓库旨在手写netty框架，采用多分支开发，每个分支都是可运行的程度：
- netty-01：带你回顾NIO的经典编程范式
- netty-02：引入单线程池将服务端的connect事件和read事件分离，利用阻塞队列异步注册read事件，后期由新线程负责事件轮询，标准的Reactor线程模型
- netty-03：采用继承的方式重构了SingleThreadEventExecutor，将NIO的事件轮询处理丢给NioEventLoop，将NIO的事件注册丢给SingleThreadEventLoop，达到职责单一的目的
- netty-04：原有的三层继承关系NioEventLoop->SingleThreadEventLoop->SingleThreadEventExecutor的基础上（NSS），逐步构建出netty的核心框架结构
  - **NioEventLoop成组**：定义两个接口，EventLoopGroup接口组合了EventLoop接口，并且这两个接口还是继承关系EventLoop->EventLoopGroup
  - **SingleThreadEventExecutor成组**：定义两个接口，EventExecutorGroup执行器组接口组合了EventLoop接口，并且这两个接口还是继承关系EventExecutor->EventExecutorGroup
  - **为了使EventLoop接口拥有EventExecutor接口的能力**，这两个接口也是继承关系EventLoop->EventExecutor
  - **为了使EventLoopGroup接口拥有EventExecutorGroup接口的能力**，这两个接口也是继承关系EventLoopGroup->EventExecutorGroup，因此与NSS对称，成组这边形成了新的三层继承关系（NMM）是NioEventLoopGroup->MultithreadEventLoopGroup->MultithreadEventExecutorGroup
- 更多分支，持续更新中

main分支涵盖以上所有分支功能，全量文档见：[docs](docs)
