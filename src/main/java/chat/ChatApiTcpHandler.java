package chat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import chat.protocol.GenericRequest;
import chat.protocol.request.RAddBan;
import chat.protocol.request.RAddFriend;
import chat.protocol.request.RAddIgnore;
import chat.protocol.request.RAddModerator;
import chat.protocol.request.RCreateRoom;
import chat.protocol.request.RDestroyAvatar;
import chat.protocol.request.REnterRoom;
import chat.protocol.request.RFriendStatus;
import chat.protocol.request.RGetAnyAvatar;
import chat.protocol.request.RGetPersistentHeaders;
import chat.protocol.request.RGetPersistentMessage;
import chat.protocol.request.RGetRoom;
import chat.protocol.request.RGetRoomSummaries;
import chat.protocol.request.RIgnoreStatus;
import chat.protocol.request.RLeaveRoom;
import chat.protocol.request.RLoginAvatar;
import chat.protocol.request.RLogoutAvatar;
import chat.protocol.request.RRegistrarGetChatServer;
import chat.protocol.request.RRemoveBan;
import chat.protocol.request.RRemoveFriend;
import chat.protocol.request.RRemoveIgnore;
import chat.protocol.request.RRemoveModerator;
import chat.protocol.request.RSendApiVersion;
import chat.protocol.request.RSendInstantMessage;
import chat.protocol.request.RSendPersistentMessage;
import chat.protocol.request.RSendRoomMessage;
import chat.protocol.request.RSetAvatarAttributes;
import chat.protocol.request.RUpdatePersistentMessage;
import chat.protocol.request.RUpdatePersistentMessages;
import chat.protocol.response.ResFriendStatus;
import chat.protocol.response.ResGetPersistentHeaders;
import chat.protocol.response.ResGetPersistentMessage;
import chat.protocol.response.ResIgnoreStatus;
import chat.protocol.response.ResRegistrarGetChatServer;
import chat.protocol.response.ResSendApiVersion;
import chat.protocol.response.ResSetAvatarAttributes;
import chat.protocol.response.ResUpdatePersistentMessage;
import chat.protocol.response.ResUpdatePersistentMessages;
import chat.protocol.response.ResponseResult;
import chat.util.ChatUnicodeString;
import chat.util.PersistentMessageStatus;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TShortObjectMap;
import gnu.trove.map.hash.TShortObjectHashMap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelHandler;

@ChannelHandler.Sharable
public class ChatApiTcpHandler extends ChannelInboundHandlerAdapter {
	
	private TShortObjectMap<PacketHandler> packetTypes;
	private static Logger logger = LogManager.getLogger(ChatApiTcpHandler.class);
	private ChatApiServer server = ChatApiServer.getInstance();

	public ChatApiTcpHandler() {
		packetTypes = new TShortObjectHashMap<>();
		insertPacketHandlers();
	}
	
	// TODO: change this entire handler system to a dependency injection based system
    private void insertPacketHandlers() {
    	packetTypes.put(GenericRequest.REQUEST_REGISTRAR_GETCHATSERVER, (cluster, packet) -> {
    		RRegistrarGetChatServer req = new RRegistrarGetChatServer();
    		req.deserialize(packet);
    		String hostname = server.getConfig().getString("hostname");
    		int port = server.getConfig().getInt("gatewayPort");
    		ResRegistrarGetChatServer res = new ResRegistrarGetChatServer();
    		res.setTrack(req.getTrack());
    		res.setHostname(new ChatUnicodeString(hostname));
    		res.setPort((short) port);
    		res.setResult(ResponseResult.CHATRESULT_SUCCESS);
    		server.getScheduler().schedule(() -> cluster.send(res.serialize()), 15, TimeUnit.SECONDS); // fix bug where server wouldnt create system rooms
    		logger.info("Registrar recieved GetChatServer requested");
    	});
    	packetTypes.put(GenericRequest.REQUEST_SETAPIVERSION, (cluster, packet) -> {
    		int version = server.getConfig().getInt("apiVersion");
    		RSendApiVersion req = new RSendApiVersion();
    		req.deserialize(packet);
    		ResSendApiVersion res = new ResSendApiVersion();
    		res.setTrack(req.getTrack());
    		res.setVersion(version);
    		if(version == req.getVersion()) {
    			res.setResult(ResponseResult.CHATRESULT_SUCCESS);
    		} else {
    			res.setResult(ResponseResult.CHATRESULT_WRONGCHATSERVERFORREQUEST);
    		}
    		cluster.send(res.serialize());
    	});
    	packetTypes.put(GenericRequest.REQUEST_LOGINAVATAR, (cluster, packet) -> {
    		RLoginAvatar req = new RLoginAvatar();
    		req.deserialize(packet);
    		if(cluster.getAddress() == null) // store the clusters address at first login since api client doesnt send us any address information otherwise
    			cluster.setAddress(req.getAddress());
    		server.handleLoginAvatar(cluster, req);
    	});
    	packetTypes.put(GenericRequest.REQUEST_SENDINSTANTMESSAGE, (cluster, packet) -> {
    		RSendInstantMessage req = new RSendInstantMessage();
    		req.deserialize(packet);
    		server.handleInstantMessage(cluster, req);
    	});
    	packetTypes.put(GenericRequest.REQUEST_LOGOUTAVATAR, (cluster, packet) -> {
    		RLogoutAvatar req = new RLogoutAvatar();
    		req.deserialize(packet);
    		server.handleLogoutAvatar(cluster, req);
    	});
    	packetTypes.put(GenericRequest.REQUEST_DESTROYAVATAR, (cluster, packet) -> {
    		RDestroyAvatar req = new RDestroyAvatar();
    		req.deserialize(packet);
    		server.handleDestroyAvatar(cluster, req);
    	});
    	packetTypes.put(GenericRequest.REQUEST_SETAVATARATTRIBUTES, (cluster, packet) -> {
    		RSetAvatarAttributes req = new RSetAvatarAttributes();
    		req.deserialize(packet);
    		ResSetAvatarAttributes res = new ResSetAvatarAttributes();
    		res.setTrack(req.getTrack());
    		ChatAvatar avatar = server.getAvatarById(req.getAvatarId());
    		if(avatar == null) {
    			res.setResult(ResponseResult.CHATRESULT_DESTAVATARDOESNTEXIST);
    			cluster.send(res.serialize());
    		} else {
    			res.setResult(ResponseResult.CHATRESULT_SUCCESS);
    			avatar.setAttributes(req.getAvatarAttributes());
    			res.setAvatar(avatar);
    			cluster.send(res.serialize());
    		}
    	});
    	packetTypes.put(GenericRequest.REQUEST_GETAVATAR, (cluster, packet) -> {}); // not used for SWG
    	packetTypes.put(GenericRequest.REQUEST_GETANYAVATAR, (cluster, packet) -> {
    		RGetAnyAvatar req = new RGetAnyAvatar();
    		req.deserialize(packet);
    		server.handleGetAnyAvatar(cluster, req);
    	});
    	packetTypes.put(GenericRequest.REQUEST_SENDPERSISTENTMESSAGE, (cluster, packet) -> {
    		System.out.println("recv mail");
    		RSendPersistentMessage req = new RSendPersistentMessage();
    		req.deserialize(packet);
    		server.handleSendPersistentMessage(cluster, req);
    	});
    	packetTypes.put(GenericRequest.REQUEST_GETPERSISTENTMESSAGE, (cluster, packet) -> {
    		RGetPersistentMessage req = new RGetPersistentMessage();
    		req.deserialize(packet);
    		ResGetPersistentMessage res = new ResGetPersistentMessage();
    		res.setTrack(req.getTrack());
    		ChatAvatar avatar = server.getAvatarById(req.getSrcAvatarId());
    		if(avatar == null) {
    			res.setResult(ResponseResult.CHATRESULT_SRCAVATARDOESNTEXIST);
    			cluster.send(res.serialize());
    			return;
    		}
    		if(!avatar.hasPm(req.getMessageId())) {
    			res.setResult(ResponseResult.CHATRESULT_PMSGNOTFOUND);
    			cluster.send(res.serialize());
    			return;
    		}
    		PersistentMessage pm = server.getPersistentMessageFromDb(req.getMessageId());
    		if(pm == null) {
    			res.setResult(ResponseResult.CHATRESULT_PMSGNOTFOUND);
    			cluster.send(res.serialize());
    			return;
    		}
    		if(pm.getStatus() == PersistentMessageStatus.NEW)
    			pm.setStatus(PersistentMessageStatus.READ);
    		res.setPm(pm);
    		res.setResult(ResponseResult.CHATRESULT_SUCCESS);
			cluster.send(res.serialize());
    	});
    	packetTypes.put(GenericRequest.REQUEST_UPDATEPERSISTENTMESSAGE, (cluster, packet) -> {
    		RUpdatePersistentMessage req = new RUpdatePersistentMessage();
    		req.deserialize(packet);
    		ResUpdatePersistentMessage res = new ResUpdatePersistentMessage();
    		res.setTrack(req.getTrack());
    		ChatAvatar avatar = server.getAvatarById(req.getSrcAvatarId());
    		if(avatar == null) {
    			res.setResult(ResponseResult.CHATRESULT_SRCAVATARDOESNTEXIST);
    			cluster.send(res.serialize());
    			return;
    		}
    		if(!avatar.hasPm(req.getMessageId())) {
    			res.setResult(ResponseResult.CHATRESULT_PMSGNOTFOUND);
    			cluster.send(res.serialize());
    			return;
    		}
    		PersistentMessage pm = server.getPersistentMessageFromDb(req.getMessageId());
    		if(pm == null) {
    			res.setResult(ResponseResult.CHATRESULT_PMSGNOTFOUND);
    			cluster.send(res.serialize());
    			return;
    		}
    		pm.setStatus(req.getStatus());
    		if(pm.getStatus() == PersistentMessageStatus.DELETED)
    			server.destroyPersistentMessage(avatar, pm);
    		else 
    			server.persistPersistentMessage(pm, true);
    		res.setResult(ResponseResult.CHATRESULT_SUCCESS);
			cluster.send(res.serialize());
    	});
    	packetTypes.put(GenericRequest.REQUEST_UPDATEPERSISTENTMESSAGES, (cluster, packet) -> {
    		RUpdatePersistentMessages req = new RUpdatePersistentMessages();
    		req.deserialize(packet);
    		ResUpdatePersistentMessages res = new ResUpdatePersistentMessages();
    		res.setTrack(req.getTrack());
    		ChatAvatar avatar = server.getAvatarById(req.getSrcAvatarId());
    		if(avatar == null) {
    			res.setResult(ResponseResult.CHATRESULT_SRCAVATARDOESNTEXIST);
    			cluster.send(res.serialize());
    			return;
    		}
    		TIntArrayList pmList = avatar.getMailIds();
    	
    		for(int pmId : pmList.toArray().clone()) {
        		PersistentMessage pm = server.getPersistentMessageFromDb(pmId);
        		if(pm == null) {
        			avatar.removeMail(pmId);
        			continue;
        		}
    			if(pm.getStatus() == req.getCurrentStatus()) {
    				pm.setStatus(req.getNewStatus());
    	    		if(pm.getStatus() == PersistentMessageStatus.DELETED)
    	    			server.destroyPersistentMessage(avatar, pm);
    	    		else
    	    			server.persistPersistentMessage(pm, true);
    			}
    		}
    		res.setResult(ResponseResult.CHATRESULT_SUCCESS);
			cluster.send(res.serialize());
    	});
    	packetTypes.put(GenericRequest.REQUEST_GETPERSISTENTHEADERS, (cluster, packet) -> {
    		System.out.println("got request for headers");
    		RGetPersistentHeaders req = new RGetPersistentHeaders();
    		req.deserialize(packet);
    		ResGetPersistentHeaders res = new ResGetPersistentHeaders();
    		res.setTrack(req.getTrack());
    		ChatAvatar avatar = server.getAvatarById(req.getSrcAvatarId());
    		if(avatar == null) {
    			res.setResult(ResponseResult.CHATRESULT_SRCAVATARDOESNTEXIST);
    			cluster.send(res.serialize());
    			return;
    		}
    		List<PersistentMessage> pmList = res.getPmList();
    		for(int pmId : avatar.getMailIds().toArray().clone()) {
        		PersistentMessage pm = server.getPersistentMessageFromDb(pmId);
        		if(pm == null) {
        			avatar.removeMail(pmId);
        			continue;
        		}
        		pmList.add(pm);
    		}
    		res.setResult(ResponseResult.CHATRESULT_SUCCESS);
			cluster.send(res.serialize());   		
    	});
    	packetTypes.put(GenericRequest.REQUEST_FRIENDSTATUS, (cluster, packet) -> {
    		RFriendStatus req = new RFriendStatus();
    		req.deserialize(packet);
    		ResFriendStatus res = new ResFriendStatus();
    		res.setTrack(req.getTrack());
    		ChatAvatar avatar = server.getAvatarById(req.getSrcAvatarId());
    		if(avatar == null) {
    			res.setResult(ResponseResult.CHATRESULT_SRCAVATARDOESNTEXIST);
    			res.setFriendsList(new ArrayList<>());
    			cluster.send(res.serialize());
    			return;
    		}
			res.setFriendsList(avatar.getFriendsList());
    		res.setResult(ResponseResult.CHATRESULT_SUCCESS);
			cluster.send(res.serialize());   		
    	});
    	packetTypes.put(GenericRequest.REQUEST_IGNORESTATUS, (cluster, packet) -> {
    		RIgnoreStatus req = new RIgnoreStatus();
    		req.deserialize(packet);
    		ResIgnoreStatus res = new ResIgnoreStatus();
    		res.setTrack(req.getTrack());
    		ChatAvatar avatar = server.getAvatarById(req.getSrcAvatarId());
    		if(avatar == null) {
    			res.setResult(ResponseResult.CHATRESULT_SRCAVATARDOESNTEXIST);
    			res.setIgnoreList(new ArrayList<>());
    			cluster.send(res.serialize());
    			return;
    		}
			res.setIgnoreList(avatar.getIgnoreList());
    		res.setResult(ResponseResult.CHATRESULT_SUCCESS);
			cluster.send(res.serialize());   		
    	});
    	packetTypes.put(GenericRequest.REQUEST_ADDFRIEND, (cluster, packet) -> {
    		System.out.println("recv add friend req");
    		RAddFriend req = new RAddFriend();
    		req.deserialize(packet);
    		server.handleAddFriend(cluster, req);
    	});
    	packetTypes.put(GenericRequest.REQUEST_REMOVEFRIEND, (cluster, packet) -> {
    		RRemoveFriend req = new RRemoveFriend();
    		req.deserialize(packet);
    		server.handleRemoveFriend(cluster, req);
    	});
    	packetTypes.put(GenericRequest.REQUEST_ADDIGNORE, (cluster, packet) -> {
    		RAddIgnore req = new RAddIgnore();
    		req.deserialize(packet);
    		server.handleAddIgnore(cluster, req);
    	});
    	packetTypes.put(GenericRequest.REQUEST_REMOVEIGNORE, (cluster, packet) -> {
    		RRemoveIgnore req = new RRemoveIgnore();
    		req.deserialize(packet);
    		server.handleRemoveIgnore(cluster, req);
    	});
    	packetTypes.put(GenericRequest.REQUEST_GETROOMSUMMARIES, (cluster, packet) -> {
    		RGetRoomSummaries req = new RGetRoomSummaries();
    		req.deserialize(packet);
    		server.handleGetRoomSummaries(cluster, req);
    	});
    	packetTypes.put(GenericRequest.REQUEST_CREATEROOM, (cluster, packet) -> {
    		RCreateRoom req = new RCreateRoom();
    		req.deserialize(packet);
    		server.handleCreateRoom(cluster, req);
    	});
    	packetTypes.put(GenericRequest.REQUEST_GETROOM, (cluster, packet) -> {
    		System.out.println("got get room req");
    		RGetRoom req = new RGetRoom();
    		req.deserialize(packet);
    		server.handleGetRoom(cluster, req);
    	});
    	packetTypes.put(GenericRequest.REQUEST_ENTERROOM, (cluster, packet) -> {
    		REnterRoom req = new REnterRoom();
    		req.deserialize(packet);
    		server.handleEnterRoom(cluster, req);
    	});
    	packetTypes.put(GenericRequest.REQUEST_LEAVEROOM, (cluster, packet) -> {
    		RLeaveRoom req = new RLeaveRoom();
    		req.deserialize(packet);
    		server.handleLeaveRoom(cluster, req);
    	});
    	packetTypes.put(GenericRequest.REQUEST_SENDROOMMESSAGE, (cluster, packet) -> {
    		RSendRoomMessage req = new RSendRoomMessage();
    		req.deserialize(packet);
    		server.handleSendRoomMessage(cluster, req);
    	});
    	packetTypes.put(GenericRequest.REQUEST_ADDMODERATOR, (cluster, packet) -> {
    		RAddModerator req = new RAddModerator();
    		req.deserialize(packet);
    		server.handleAddModerator(cluster, req);
    	});
    	packetTypes.put(GenericRequest.REQUEST_REMOVEMODERATOR, (cluster, packet) -> {
    		RRemoveModerator req = new RRemoveModerator();
    		req.deserialize(packet);
    		server.handleRemoveModerator(cluster, req);
    	});
    	packetTypes.put(GenericRequest.REQUEST_ADDBAN, (cluster, packet) -> {
    		RAddBan req = new RAddBan();
    		req.deserialize(packet);
    		server.handleAddBan(cluster, req);
    	});
    	packetTypes.put(GenericRequest.REQUEST_REMOVEBAN, (cluster, packet) -> {
    		RRemoveBan req = new RRemoveBan();
    		req.deserialize(packet);
    		server.handleRemoveBan(cluster, req);
    	});
    	
	}

	@Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
		ChatApiClient cluster = server.getClusterByChannel(ctx.channel());
		// msg comes in as an unpooled unsafe buffer in native memory
		ByteBuf unsafe = (ByteBuf) msg;
    	ByteBuffer packet = ByteBuffer.allocate(((ByteBuf) msg).readableBytes()).order(ByteOrder.LITTLE_ENDIAN);
    	unsafe.getBytes(0, packet);
    	packet.position(0);
    	if(packet.capacity() < 10) {
    		logger.warn("Recieved packet of size < 6 bytes");
    		ctx.writeAndFlush(unsafe);
			return;
    	}
    	if(cluster == null) {
			logger.warn("ChatApiClient object not found for given channel");
			return;
		}
    	packet.getInt(); //length of packet in big endian
    	short type = packet.getShort(4);
    	PacketHandler handler = packetTypes.get(type);
    	if(handler == null) {
    		logger.info("Unhandled packet type: {}", type);
    		System.out.println(type);
    		return;
    	}
    	// we are in the IO thread and the submit the handler function to the packet processor to avoid stalling IO operations
    	server.getPacketProcessor().execute(() -> {
    		handler.handle(cluster, packet);
    	});
    }
	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
    
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		server.removeCluster(ctx.channel());
    }
    
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
		server.addCluster(ctx.channel());
    }

}
