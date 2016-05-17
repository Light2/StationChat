package chat;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChatApiTcpListener {
	
	private final int port;
	private Thread recvThread;
	private ServerBootstrap bootstrap;
	private static Logger logger = LogManager.getLogger(ChatApiTcpListener.class);
	
	public ChatApiTcpListener(int port) {
		this.port = port;
	}
	
	public void start() {
		recvThread = new Thread(() -> {
			EventLoopGroup group = new NioEventLoopGroup();
			try {
				bootstrap = new ServerBootstrap();
				bootstrap.group(group)
				.channel(NioServerSocketChannel.class)
				.option(ChannelOption.SO_KEEPALIVE, true)
				.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
				.childHandler(new ChatApiTcpHandler());
				logger.info("TCP Server starting on port {}", port);
				bootstrap.bind(port).sync().channel().closeFuture().await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				group.shutdownGracefully();
			}	
		});
		recvThread.start();
		
	}

	public int getPort() {
		return port;
	}

}
