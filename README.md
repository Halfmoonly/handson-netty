# handson-netty
手写netty框架采用多分支开发，每个分支都是可运行的程度：
- netty-01：带你回顾NIO的经典编程范式
- netty-02：引入单线程池异步注册事件，将服务端的connect事件和read事件分离，利用阻塞队列异步注册read事件，后期由新线程负责事件轮询，标准的Reactor线程模型
- netty-03：采用继承的方式重构了SingleThreadEventExecutor，将NIO的事件轮询处理丢给NioEventLoop，将NIO的事件注册丢给SingleThreadEventLoop，达到职责单一的目的
- 更多分支，持续更新中

main分支涵盖以上所有分支功能，全量文档见：[docs](docs)
