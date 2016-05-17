package chat;

import java.nio.ByteBuffer;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

public class ChatApiClient {
	
	private Channel channel;
	
	public ChatApiClient(Channel channel) {
		this.channel = channel;
	}

	public Channel getChannel() {
		return channel;
	}

	public void setChannel(Channel channel) {
		this.channel = channel;
	}

	public void send(ByteBuffer byteBuffer) {
		byteBuffer.flip();
		ChannelFuture future = channel.writeAndFlush(byteBuffer);
		if(!future.isSuccess()) {
			System.out.println(future.cause());
		}
	}

}
