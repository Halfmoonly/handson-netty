package com.pp.netty.test;

//public class NettyTest {
//
//    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
//        //创建一个selector
//        Selector selector = Selector.open();
//        //创建一个服务端的通道
//        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
//        //创建一个callable，异步任务
//        Callable callable = new Callable() {
//            @Override
//            public Object call() throws Exception {
//                //将该channel注册到selector上,关注接收事件
//                serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
//                //返回该服务端channel
//                return serverSocketChannel;
//            }
//        };
//        DefaultPromise<ServerSocketChannel> promise = new DefaultPromise(callable);
//        //异步执行callable任务
//        Thread thread = new Thread(promise);
//        //启动线程
//        thread.start();
//        //得到服务端的channel
//        ServerSocketChannel serverSocketChannel = promise.get();
//        //服务端channel绑定端口号
//        serverSocketChannel.bind(new InetSocketAddress("127.0.0.1",8080));
//    }
//}
