package chat.protocol.request;

import java.nio.ByteBuffer;

import chat.ChatApiTcpHandler;
import chat.protocol.GenericRequest;
import chat.util.ChatUnicodeString;

public class RGetRoom extends GenericRequest {

	private ChatUnicodeString roomAddress = new ChatUnicodeString();

	@Override
	public ByteBuffer serialize() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void deserialize(ByteBuffer buf) {
		type = buf.getShort();
		track = buf.getInt();
		roomAddress.deserialize(buf);
		System.out.println(ChatApiTcpHandler.bytesToHex(buf.array()));
	}

	public ChatUnicodeString getRoomAddress() {
		return roomAddress;
	}

	public void setRoomAddress(ChatUnicodeString roomAddress) {
		this.roomAddress = roomAddress;
	}

}
