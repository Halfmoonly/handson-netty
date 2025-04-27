//import com.pp.netty.bootstrap.Bootstrap;
//import com.pp.netty.bootstrap.ServerBootstrap;
//import com.pp.netty.channel.nio.NioEventLoop;
//import org.junit.Test;
//
//import java.io.IOException;
//import java.nio.channels.ServerSocketChannel;
//import java.nio.channels.SocketChannel;
//
//public class TestNio {
//    @Test
//    public void testServer() throws Exception {
//        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
//        ServerBootstrap serverBootstrap = new ServerBootstrap();
//        NioEventLoop nioEventLoop = new NioEventLoop(serverSocketChannel, null);
//        serverBootstrap.nioEventLoop(nioEventLoop).
//                        serverSocketChannel(serverSocketChannel);
//        serverBootstrap.bind("127.0.0.1",8080);
//    }
//
//    @Test
//    public void testClient() throws Exception {
//        SocketChannel socketChannel = SocketChannel.open();
//        Bootstrap bootstrap = new Bootstrap();
//        bootstrap.nioEventLoop(new NioEventLoop(null,socketChannel)).
//                  socketChannel(socketChannel);
//        bootstrap.connect("127.0.0.1",8080);
//    }
//}
