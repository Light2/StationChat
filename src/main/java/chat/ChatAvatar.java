package chat;

import chat.util.ChatUnicodeString;

public class ChatAvatar {
	
	public static final int AVATARATTR_INVISIBLE    = 1 << 0;
	public static final int AVATARATTR_GM           = 1 << 1;
	public static final int AVATARATTR_SUPERGM      = 1 << 2;
	public static final int AVATARATTR_SUPERSNOOP   = 1 << 3;
	public static final int AVATARATTR_EXTENDED     = 1 << 4;

	private ChatUnicodeString name;
	private ChatUnicodeString address;
	private ChatUnicodeString server;
	private ChatUnicodeString gateway;
	private ChatUnicodeString loginLocation;
	private int avatarId;
	private int userId; // station ID
	private int serverId;
	private int gatewayId;
	private int attributes;

}
