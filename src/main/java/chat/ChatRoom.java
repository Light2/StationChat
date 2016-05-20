package chat;

import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import chat.protocol.GenericMessage;
import chat.util.ChatUnicodeString;

public class ChatRoom {
	
	public static final int ROOMATTR_PRIVATE = 1 << 0;
	public static final int ROOMATTR_MODERATED = 1 << 1;
	public static final int ROOMATTR_PERSISTENT = 1 << 2;
	public static final int ROOMATTR_LOCAL_WORLD = 1 << 4;
	public static final int ROOMATTR_LOCAL_GAME = 1 << 5;

	private ChatUnicodeString creatorName;
	private ChatUnicodeString creatorAddress;
	private int creatorId;
	private ChatUnicodeString roomName;
	private ChatUnicodeString roomTopic;
	private ChatUnicodeString roomPrefix;
	private ChatUnicodeString roomAddress;
	private ChatUnicodeString roomPassword;
	private int roomAttributes;
	private int maxRoomSize;
	private int roomId;
	private int createTime;
	private int nodeLevel;
	private TIntList avatarList = new TIntArrayList();
	private TIntList adminList = new TIntArrayList();
	private TIntList moderatorList = new TIntArrayList();
	private TIntList tmpModList = new TIntArrayList();
	private TIntList banList = new TIntArrayList();
	private TIntList inviteList = new TIntArrayList();
	private TIntList voiceList = new TIntArrayList();
	
	public ChatUnicodeString getCreatorName() {
		return creatorName;
	}
	
	public void setCreatorName(ChatUnicodeString creatorName) {
		this.creatorName = creatorName;
	}

	public ChatUnicodeString getCreatorAddress() {
		return creatorAddress;
	}

	public void setCreatorAddress(ChatUnicodeString creatorAddress) {
		this.creatorAddress = creatorAddress;
	}

	public int getCreatorId() {
		return creatorId;
	}

	public void setCreatorId(int creatorId) {
		this.creatorId = creatorId;
	}

	public ChatUnicodeString getRoomName() {
		return roomName;
	}

	public void setRoomName(ChatUnicodeString roomName) {
		this.roomName = roomName;
	}

	public ChatUnicodeString getRoomTopic() {
		return roomTopic;
	}

	public void setRoomTopic(ChatUnicodeString roomTopic) {
		this.roomTopic = roomTopic;
	}

	public ChatUnicodeString getRoomPrefix() {
		return roomPrefix;
	}

	public void setRoomPrefix(ChatUnicodeString roomPrefix) {
		this.roomPrefix = roomPrefix;
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

	public int getMaxRoomSize() {
		return maxRoomSize;
	}

	public void setMaxRoomSize(int maxRoomSize) {
		this.maxRoomSize = maxRoomSize;
	}

	public int getRoomId() {
		return roomId;
	}

	public void setRoomId(int roomId) {
		this.roomId = roomId;
	}

	public int getCreateTime() {
		return createTime;
	}

	public void setCreateTime(int createTime) {
		this.createTime = createTime;
	}

	public int getNodeLevel() {
		return nodeLevel;
	}

	public void setNodeLevel(int nodeLevel) {
		this.nodeLevel = nodeLevel;
	}

	public TIntList getAvatarList() {
		return avatarList;
	}

	public void setAvatarList(TIntList avatarList) {
		this.avatarList = avatarList;
	}

	public TIntList getAdminList() {
		return adminList;
	}

	public void setAdminList(TIntList adminList) {
		this.adminList = adminList;
	}

	public TIntList getModeratorList() {
		return moderatorList;
	}

	public void setModeratorList(TIntList moderatorList) {
		this.moderatorList = moderatorList;
	}

	public TIntList getTmpModList() {
		return tmpModList;
	}

	public void setTmpModList(TIntList tmpModList) {
		this.tmpModList = tmpModList;
	}

	public TIntList getBanList() {
		return banList;
	}

	public void setBanList(TIntList banList) {
		this.banList = banList;
	}

	public TIntList getInviteList() {
		return inviteList;
	}

	public void setInviteList(TIntList inviteList) {
		this.inviteList = inviteList;
	}

	public TIntList getVoiceList() {
		return voiceList;
	}

	public void setVoiceList(TIntList voiceList) {
		this.voiceList = voiceList;
	}
	
	public boolean isPrivate() {
		return (getRoomAttributes() & ROOMATTR_PRIVATE) == 1;
	}
	
	public boolean isModerated() {
		return (getRoomAttributes() & ROOMATTR_MODERATED) == 1;
	}

	public boolean isPersistent() {
		return (getRoomAttributes() & ROOMATTR_PERSISTENT) == 1;
	}

	public boolean isLocalWorld() {
		return (getRoomAttributes() & ROOMATTR_LOCAL_WORLD) == 1;
	}

	public boolean isLocalGame() {
		return (getRoomAttributes() & ROOMATTR_LOCAL_GAME) == 1;
	}
	
	public byte[] serialize() {
		ByteBuf buf = GenericMessage.alloc.heapBuffer().order(ByteOrder.LITTLE_ENDIAN);
		buf.writeBytes(creatorName.serialize());
		buf.writeBytes(creatorAddress.serialize());
		buf.writeInt(creatorId);
		buf.writeBytes(roomName.serialize());
		buf.writeBytes(roomTopic.serialize());
		buf.writeBytes(roomPrefix.serialize());
		buf.writeBytes(roomAddress.serialize());
		buf.writeBytes(roomPassword.serialize());
		buf.writeInt(roomAttributes);
		buf.writeInt(maxRoomSize);
		buf.writeInt(roomId);
		buf.writeInt(createTime);
		buf.writeInt(nodeLevel);
		buf.writeInt(avatarList.size());
		for(int avatarId : avatarList.toArray()) {
			buf.writeInt(avatarId);
		}
		buf.writeInt(adminList.size());
		for(int adminId : adminList.toArray()) {
			buf.writeInt(adminId);
		}
		buf.writeInt(moderatorList.size());
		for(int modId : moderatorList.toArray()) {
			buf.writeInt(modId);
		}
		buf.writeInt(tmpModList.size());
		for(int modId : tmpModList.toArray()) {
			buf.writeInt(modId);
		}
		buf.writeInt(banList.size());
		for(int bannedId : banList.toArray()) {
			buf.writeInt(bannedId);
		}
		buf.writeInt(inviteList.size());
		for(int inviteId : inviteList.toArray()) {
			buf.writeInt(inviteId);
		}
		buf.writeInt(voiceList.size());
		for(int voiceId : voiceList.toArray()) {
			buf.writeInt(voiceId);
		}
		return buf.array();
	}
	
	public byte[] serializeSummary() {
		ByteBuf buf = GenericMessage.alloc.heapBuffer().order(ByteOrder.LITTLE_ENDIAN);
		buf.writeBytes(roomAddress.serialize());
		buf.writeBytes(roomTopic.serialize());
		buf.writeInt(roomAttributes);
		buf.writeInt(avatarList.size());
		buf.writeInt(maxRoomSize);
		return buf.array();
	}
	
}
