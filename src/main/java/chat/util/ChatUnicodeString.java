package chat.util;

import java.nio.ByteOrder;

import io.netty.buffer.ByteBuf;
import chat.protocol.GenericMessage;

public class ChatUnicodeString {
	
	private String string;
	
	public ChatUnicodeString(String string) {
		setString(string);
	}

	public String getString() {
		return string;
	}

	public void setString(String string) {
		this.string = string;
	}
	
	public ByteBuf serialize() {
		int length = string.length();
		ByteBuf buf = GenericMessage.alloc.buffer(4 + length).order(ByteOrder.LITTLE_ENDIAN);
		buf.writeInt(length);
		buf.writeBytes(string.getBytes());
		return buf;
	}

}
