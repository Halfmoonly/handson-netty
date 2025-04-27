# handson-netty
手写netty框架采用多分支开发，每个分支都是可运行的程度：
- netty-01：带你回顾NIO的经典编程范式
- netty-02：引入单线程池，将服务端的connect事件和read事件分离，由新的线程专门处理read事件，标准的Reactor线程模型
- netty-03：引入NioEventLoop，解耦并重构了服务器单线程执行器SingleThreadEventExecutor。初步尝试服务端工作线程组WorkGroup
- 更多分支，持续更新中

main分支涵盖以上所有分支功能，全量文档见：[docs](docs)
