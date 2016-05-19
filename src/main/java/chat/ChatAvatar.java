package chat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import chat.util.ChatUnicodeString;

public class ChatAvatar {
	
	public static final int AVATARATTR_INVISIBLE    = 1 << 0;
	public static final int AVATARATTR_GM           = 1 << 1;
	public static final int AVATARATTR_SUPERGM      = 1 << 2;
	public static final int AVATARATTR_SUPERSNOOP   = 1 << 3;
	public static final int AVATARATTR_EXTENDED     = 1 << 4;

	private ChatUnicodeString name;
	private ChatUnicodeString address;
	private ChatUnicodeString server = new ChatUnicodeString(); // can just be empty dont need
	private ChatUnicodeString gateway = new ChatUnicodeString(); // can just be empty dont need
	private ChatUnicodeString loginLocation;
	private int avatarId;
	private int userId; // station ID
	private int serverId;
	private int gatewayId;
	private int attributes;
	private transient boolean isLoggedIn = false;
	private transient ChatApiClient cluster;
	private TIntArrayList mailIds = new TIntArrayList();
	private transient TIntObjectMap<PersistentMessage> pmList = new TIntObjectHashMap<>();
	
	public ChatUnicodeString getName() {
		return name;
	}
	
	public void setName(ChatUnicodeString name) {
		this.name = name;
	}

	public ChatUnicodeString getAddress() {
		return address;
	}

	public void setAddress(ChatUnicodeString address) {
		this.address = address;
	}

	public ChatUnicodeString getServer() {
		return server;
	}

	public void setServer(ChatUnicodeString server) {
		this.server = server;
	}

	public ChatUnicodeString getGateway() {
		return gateway;
	}

	public void setGateway(ChatUnicodeString gateway) {
		this.gateway = gateway;
	}

	public ChatUnicodeString getLoginLocation() {
		return loginLocation;
	}

	public void setLoginLocation(ChatUnicodeString loginLocation) {
		this.loginLocation = loginLocation;
	}

	public int getAvatarId() {
		return avatarId;
	}

	public void setAvatarId(int avatarId) {
		this.avatarId = avatarId;
	}

	public int getUserId() {
		return userId;
	}

	public void setUserId(int userId) {
		this.userId = userId;
	}

	public int getServerId() {
		return serverId;
	}

	public void setServerId(int serverId) {
		this.serverId = serverId;
	}

	public int getGatewayId() {
		return gatewayId;
	}

	public void setGatewayId(int gatewayId) {
		this.gatewayId = gatewayId;
	}

	public int getAttributes() {
		return attributes;
	}

	public void setAttributes(int attributes) {
		this.attributes = attributes;
	}

	public boolean isLoggedIn() {
		return isLoggedIn;
	}

	public void setLoggedIn(boolean isLoggedIn) {
		this.isLoggedIn = isLoggedIn;
	}

	public ChatApiClient getCluster() {
		return cluster;
	}

	public void setCluster(ChatApiClient cluster) {
		this.cluster = cluster;
	}

	public TIntArrayList getMailIds() {
		return mailIds;
	}

	public void setMailIds(TIntArrayList mailIds) {
		this.mailIds = mailIds;
	}

	public byte[] serialize() {
		ByteBuffer buf = ByteBuffer.allocate(40 + name.getStringLength() * 2 + address.getStringLength() * 2 + loginLocation.getStringLength() * 2 + server.getStringLength() * 2 + gateway.getStringLength() * 2).order(ByteOrder.LITTLE_ENDIAN);
		buf.putInt(avatarId);
		buf.putInt(userId);
		buf.put(name.serialize());
		buf.put(address.serialize());
		buf.putInt(attributes);
		buf.put(loginLocation.serialize());
		buf.put(server.serialize());
		buf.put(gateway.serialize());
		buf.putInt(serverId);
		buf.putInt(gatewayId);
		return buf.array();
	}
	
	public String getAddressAndName() {
		return address.getString() + "+" + name.getString();
	}
	
	public boolean isInvisible() {
		return (getAttributes() & AVATARATTR_INVISIBLE) == 1;
	}
	
	public boolean isGm() {
		return (getAttributes() & AVATARATTR_GM) == 1;
	}
	
	public boolean isSuperGm() {
		return (getAttributes() & AVATARATTR_SUPERGM) == 1;
	}
	
	public boolean isSuperSnoop() {
		return (getAttributes() & AVATARATTR_SUPERSNOOP) == 1;
	}

	public void addMail(PersistentMessage pm) {
		mailIds.add(pm.getMessageId());
		pmList.put(pm.getMessageId(), pm);
	}

	public TIntObjectMap<PersistentMessage> getPmList() {
		return pmList;
	}

	public void setPmList(TIntObjectMap<PersistentMessage> pmList) {
		this.pmList = pmList;
	}
	
	public PersistentMessage getPm(int messageId) {
		return pmList.get(messageId);
	}

	public void removeMail(PersistentMessage pm) {
		pmList.remove(pm.getMessageId());
		mailIds.remove(pm.getMessageId());
	}
	
}
