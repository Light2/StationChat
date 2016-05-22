package chat;

import io.netty.buffer.ByteBuf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

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
	private ChatUnicodeString roomPrefix = new ChatUnicodeString();
	private ChatUnicodeString roomAddress;
	private ChatUnicodeString roomPassword;
	private int roomAttributes;
	private int maxRoomSize = Integer.MAX_VALUE;
	private int roomId;
	private int createTime;
	private int nodeLevel;
	/*private TIntList avatarList = new TIntArrayList();
	private TIntList adminList = new TIntArrayList();
	private TIntList moderatorList = new TIntArrayList();
	private TIntList tmpModList = new TIntArrayList();
	private TIntList banList = new TIntArrayList();
	private TIntList inviteList = new TIntArrayList();
	private TIntList voiceList = new TIntArrayList();*/
	
	private List<ChatAvatar> avatarList = new ArrayList<>();
	private List<ChatAvatar> adminList = new ArrayList<>();
	private List<ChatAvatar> moderatorList = new ArrayList<>();
	private List<ChatAvatar> tmpModList = new ArrayList<>();
	private List<ChatAvatar> banList = new ArrayList<>();
	private List<ChatAvatar> inviteList = new ArrayList<>();
	private List<ChatAvatar> voiceList = new ArrayList<>();

	
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

	public List<ChatAvatar> getAvatarList() {
		return avatarList;
	}

	public void setAvatarList(List<ChatAvatar> avatarList) {
		this.avatarList = avatarList;
	}

	public List<ChatAvatar> getAdminList() {
		return adminList;
	}

	public void setAdminList(List<ChatAvatar> adminList) {
		this.adminList = adminList;
	}

	public List<ChatAvatar> getModeratorList() {
		return moderatorList;
	}

	public void setModeratorList(List<ChatAvatar> moderatorList) {
		this.moderatorList = moderatorList;
	}

	public List<ChatAvatar> getTmpModList() {
		return tmpModList;
	}

	public void setTmpModList(List<ChatAvatar> tmpModList) {
		this.tmpModList = tmpModList;
	}

	public List<ChatAvatar> getBanList() {
		return banList;
	}

	public void setBanList(List<ChatAvatar> banList) {
		this.banList = banList;
	}

	public List<ChatAvatar> getInviteList() {
		return inviteList;
	}

	public void setInviteList(List<ChatAvatar> inviteList) {
		this.inviteList = inviteList;
	}

	public List<ChatAvatar> getVoiceList() {
		return voiceList;
	}

	public void setVoiceList(List<ChatAvatar> voiceList) {
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
		//ByteArrayOutputStream buf = new ByteArrayOutputStream();
		ByteBuf buf = GenericMessage.alloc.buffer().order(ByteOrder.LITTLE_ENDIAN);
		//try {
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
			for(ChatAvatar avatar : avatarList) {
				buf.writeBytes(avatar.serialize());
			}
			buf.writeInt(adminList.size());
			for(ChatAvatar admin : adminList) {
				buf.writeBytes(admin.serialize());
			}
			buf.writeInt(moderatorList.size());
			for(ChatAvatar mod : moderatorList) {
				buf.writeBytes(mod.serialize());
			}
			buf.writeInt(tmpModList.size());
			for(ChatAvatar mod : tmpModList) {
				buf.writeBytes(mod.serialize());
			}
			buf.writeInt(banList.size());
			for(ChatAvatar banned : banList) {
				buf.writeBytes(banned.serialize());
			}
			buf.writeInt(inviteList.size());
			for(ChatAvatar invite : inviteList) {
				buf.writeBytes(invite.serialize());
			}
			buf.writeInt(voiceList.size());
			for(ChatAvatar voice : voiceList) {
				buf.writeBytes(voice.serialize());
			}
		//} catch (IOException e) {
		//	e.printStackTrace();
		//}
    	ByteBuffer packet = ByteBuffer.allocate(buf.writerIndex()).order(ByteOrder.LITTLE_ENDIAN);
    	buf.getBytes(0, packet);
		return packet.array();
	}
	
	public byte[] serializeSummary() {
		ByteBuf buf = GenericMessage.alloc.buffer().order(ByteOrder.LITTLE_ENDIAN);
		buf.writeBytes(roomAddress.serialize());
		//buf.writeBytes(getFullAddress().getBytes());
		buf.writeBytes(roomTopic.serialize());
		buf.writeInt(roomAttributes);
		buf.writeInt(avatarList.size());
		buf.writeInt(maxRoomSize);
    	ByteBuffer packet = ByteBuffer.allocate(buf.writerIndex()).order(ByteOrder.LITTLE_ENDIAN);
    	buf.getBytes(0, packet);
		return packet.array();
	}
	
	public void addAvatar(ChatAvatar avatar) {
		avatarList.add(avatar);
	}
	
	public void addAdmin(ChatAvatar avatar) {
		adminList.add(avatar);
	}
		
}
