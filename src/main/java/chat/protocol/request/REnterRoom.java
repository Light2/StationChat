package chat.protocol.request;

import java.nio.ByteBuffer;

import chat.protocol.GenericRequest;
import chat.util.ChatUnicodeString;

public class REnterRoom extends GenericRequest {

	private int srcAvatarId;
	private ChatUnicodeString srcAddress = new ChatUnicodeString();
	private ChatUnicodeString roomAddress = new ChatUnicodeString();
	private ChatUnicodeString roomPassword = new ChatUnicodeString();
	private boolean passiveCreate;
	private int roomAttributes; 
	private int maxRoomSize; 
	private ChatUnicodeString roomTopic = new ChatUnicodeString();
	private boolean requestingEntry; // not used for SWG

	@Override
	public ByteBuffer serialize() {
		return null;
	}

	@Override
	public void deserialize(ByteBuffer buf) {
		type = buf.getShort();
		track = buf.getInt();
		srcAvatarId = buf.getInt();
		roomAddress.deserialize(buf);
	    roomPassword.deserialize(buf);
	    passiveCreate = buf.get() != 0;
	    if(passiveCreate) {
	    	roomTopic.deserialize(buf);
			roomAttributes = buf.getInt();
			maxRoomSize = buf.getInt();
	    }
	    requestingEntry = buf.get() != 0;
		srcAddress.deserialize(buf);
	}

	public int getSrcAvatarId() {
		return srcAvatarId;
	}

	public void setSrcAvatarId(int srcAvatarId) {
		this.srcAvatarId = srcAvatarId;
	}

	public ChatUnicodeString getSrcAddress() {
		return srcAddress;
	}

	public void setSrcAddress(ChatUnicodeString srcAddress) {
		this.srcAddress = srcAddress;
	}

	public ChatUnicodeString getRoomAddress() {
		return roomAddress;
	}

	public void setRoomAddress(ChatUnicodeString roomAddress) {
		this.roomAddress = roomAddress;
	}

	public ChatUnicodeString getRoomPassword() {
		return roomPassword;
	}

	public void setRoomPassword(ChatUnicodeString roomPassword) {
		this.roomPassword = roomPassword;
	}

	public int getRoomAttributes() {
		return roomAttributes;
	}

	public void setRoomAttributes(int roomAttributes) {
		this.roomAttributes = roomAttributes;
	}

	public boolean isPassiveCreate() {
		return passiveCreate;
	}

	public void setPassiveCreate(boolean passiveCreate) {
		this.passiveCreate = passiveCreate;
	}

	public int getMaxRoomSize() {
		return maxRoomSize;
	}

	public void setMaxRoomSize(int maxRoomSize) {
		this.maxRoomSize = maxRoomSize;
	}

	public ChatUnicodeString getRoomTopic() {
		return roomTopic;
	}

	public void setRoomTopic(ChatUnicodeString roomTopic) {
		this.roomTopic = roomTopic;
	}

	public boolean isRequestingEntry() {
		return requestingEntry;
	}

	public void setRequestingEntry(boolean requestingEntry) {
		this.requestingEntry = requestingEntry;
	}

}
