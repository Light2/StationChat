package chat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import chat.protocol.GenericMessage;
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
		ByteBuf buf = GenericMessage.alloc.buffer(byteBuffer.capacity()).order(ByteOrder.LITTLE_ENDIAN);
		byteBuffer.flip();
		buf.writeBytes(byteBuffer);
		channel.writeAndFlush(buf);
	}

}
