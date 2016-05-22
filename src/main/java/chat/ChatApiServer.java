package chat;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import io.netty.channel.Channel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.ReadOptions;

import static org.fusesource.leveldbjni.JniDBFactory.*;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import chat.protocol.message.MFriendLogin;
import chat.protocol.message.MFriendLogout;
import chat.protocol.message.MPersistentMessage;
import chat.protocol.message.MRoomMessage;
import chat.protocol.message.MSendInstantMessage;
import chat.protocol.request.RAddFriend;
import chat.protocol.request.RAddIgnore;
import chat.protocol.request.RCreateRoom;
import chat.protocol.request.RDestroyAvatar;
import chat.protocol.request.REnterRoom;
import chat.protocol.request.RGetAnyAvatar;
import chat.protocol.request.RGetRoom;
import chat.protocol.request.RGetRoomSummaries;
import chat.protocol.request.RLoginAvatar;
import chat.protocol.request.RLogoutAvatar;
import chat.protocol.request.RRemoveFriend;
import chat.protocol.request.RRemoveIgnore;
import chat.protocol.request.RSendInstantMessage;
import chat.protocol.request.RSendPersistentMessage;
import chat.protocol.request.RSendRoomMessage;
import chat.protocol.response.ResAddFriend;
import chat.protocol.response.ResAddIgnore;
import chat.protocol.response.ResCreateRoom;
import chat.protocol.response.ResDestroyAvatar;
import chat.protocol.response.ResEnterRoom;
import chat.protocol.response.ResGetAnyAvatar;
import chat.protocol.response.ResGetRoom;
import chat.protocol.response.ResGetRoomSummaries;
import chat.protocol.response.ResLoginAvatar;
import chat.protocol.response.ResLogoutAvatar;
import chat.protocol.response.ResRemoveFriend;
import chat.protocol.response.ResRemoveIgnore;
import chat.protocol.response.ResSendInstantMessage;
import chat.protocol.response.ResSendPersistentMessage;
import chat.protocol.response.ResSendRoomMessage;
import chat.protocol.response.ResponseResult;
import chat.util.ChatUnicodeString;
import chat.util.Config;
import chat.util.PersistentMessageStatus;

public class ChatApiServer {
	
	private final Config config;
	private static ChatApiServer instance;
	private ChatApiTcpListener registrar;
	private ChatApiTcpListener gateway;
	private static Logger logger = LogManager.getLogger();
	private static final ThreadLocal<Kryo> kryos = new ThreadLocal<Kryo>() {
	    protected Kryo initialValue() {
	        Kryo kryo = new Kryo();
	        //TODO: configure kryo instance, customize settings
	        return kryo;
	    };
	};

	private ExecutorService packetProcessor = Executors.newSingleThreadExecutor();
	private List<ChatApiClient> connectedClusters = new CopyOnWriteArrayList<>();
	private DB avatarDb;
	private DB chatRoomDb;
	private DB mailDb;
	private final Map<String, ChatAvatar> onlineAvatars = new HashMap<>();
	private ExecutorService persister;
	private int highestAvatarId;
	private TIntIntMap roomMessageIdMap = new TIntIntHashMap();
	private Map<String, ChatRoom> roomMap = new HashMap<>();
	private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	
	public ChatApiServer() {
		config = new Config("config.cfg");
	}
	
	public static void main(String[] args) throws InterruptedException, IOException {
		instance = new ChatApiServer();
		System.setProperty("Log4jContextSelector", "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");
		instance.start();
		while(true) {
			Thread.sleep(10000);
		}
	}
	
	private void start() throws IOException {
		persister = Executors.newFixedThreadPool(config.getInt("persisterThreads"));
		Options levelDbOptions = new Options();
		levelDbOptions.createIfMissing(true);
		levelDbOptions.cacheSize(config.getInt("levelDbCache"));
		avatarDb = factory.open(new File("./db/chatAvatars"), levelDbOptions);
		chatRoomDb = factory.open(new File("./db/chatRooms"), levelDbOptions);
		if(config.getBoolean("compressMails"))
			levelDbOptions.compressionType(CompressionType.SNAPPY);
		mailDb = factory.open(new File("./db/mails"), levelDbOptions);
		getHighestAvatarIdFromDatabase();
		registrar = new ChatApiTcpListener(config.getInt("registrarPort"));
		gateway = new ChatApiTcpListener(config.getInt("gatewayPort"));
		registrar.start();
		gateway.start();
	}
	
	private void getHighestAvatarIdFromDatabase() {
		byte[] buf = avatarDb.get("highestId".getBytes());
		if(buf == null) {
			highestAvatarId = 0;
			Integer idObj = new Integer(highestAvatarId);
			Output output = new Output(new ByteArrayOutputStream());
			kryos.get().writeClassAndObject(output, idObj);
			avatarDb.put("highestId".getBytes(), output.toBytes());
			output.close();
			return;
		}
		Input input = new Input(new ByteArrayInputStream(buf));
		Integer idObj = (Integer) kryos.get().readClassAndObject(input);
		highestAvatarId = idObj;
		input.close();
	}
	
	private int getNewAvatarId() {
		int nextAvatarId = ++highestAvatarId;
		Integer idObj = new Integer(highestAvatarId);
		Output output = new Output(new ByteArrayOutputStream());
		kryos.get().writeClassAndObject(output, idObj);
		avatarDb.put("highestId".getBytes(), output.toBytes());
		output.close();
		return nextAvatarId;
	}
	
	public void handleLoginAvatar(ChatApiClient cluster, RLoginAvatar request) {
		String fullAddress = request.getAddress().getString() + "+" + request.getName().getString();
		ResLoginAvatar response = new ResLoginAvatar();
		response.setTrack(request.getTrack());
		if(onlineAvatars.get(fullAddress) != null) {
			response.setAvatar(onlineAvatars.get(fullAddress));
			response.setResult(ResponseResult.CHATRESULT_DUPLICATELOGIN);
			cluster.send(response.serialize());
			return;
		}
		ChatAvatar avatar = getAvatarFromDatabase(fullAddress);
		if(avatar != null) {
			System.out.println("Got avatar from DB");
			loginAvatar(cluster, avatar);
		} else {
			System.out.println("Creating new avatar");
			avatar = createAvatar(cluster, request, fullAddress);
		}
		response.setAvatar(avatar);
		response.setResult(ResponseResult.CHATRESULT_SUCCESS);
		cluster.send(response.serialize());
	}
	
	public void handleInstantMessage(ChatApiClient cluster, RSendInstantMessage req) {
		// TODO: add ignore list check and gm stuff if needed
		int srcAvatarId = req.getSrcAvatarId();
		ResSendInstantMessage res = new ResSendInstantMessage();
		res.setTrack(req.getTrack());
		ChatAvatar srcAvatar = getAvatarById(srcAvatarId);
		if(srcAvatar == null) {
			res.setResult(ResponseResult.CHATRESULT_SRCAVATARDOESNTEXIST);
			cluster.send(res.serialize());
			return;
		}
		ChatAvatar destAvatar = onlineAvatars.get(req.getDestAddress() + "+" + req.getDestName());
		if(destAvatar == null || destAvatar.isInvisible()) {
			res.setResult(ResponseResult.CHATRESULT_DESTAVATARDOESNTEXIST);
			cluster.send(res.serialize());
			return;
		}
		if(!srcAvatar.isGm() && !srcAvatar.isSuperGm() && destAvatar.hasIgnore(srcAvatar.getAvatarId())) {
			res.setResult(ResponseResult.CHATRESULT_IGNORING);
			cluster.send(res.serialize());
			return;					
		}
		res.setResult(ResponseResult.CHATRESULT_SUCCESS);
		MSendInstantMessage msg = new MSendInstantMessage();
		msg.setDestAvatarId(destAvatar.getAvatarId());
		msg.setMessage(req.getMessage());
		msg.setSrcAvatar(srcAvatar);
		msg.setOob(req.getOob());
		destAvatar.getCluster().send(msg.serialize());
		cluster.send(res.serialize());
	}
	
	private ChatAvatar createAvatar(ChatApiClient cluster, RLoginAvatar request, String fullAddress) {
		ChatAvatar avatar = new ChatAvatar();
		avatar.setAvatarId(getNewAvatarId());
		avatar.setAddress(request.getAddress());
		avatar.setName(request.getName());
		avatar.setAttributes(request.getLoginAttributes());
		avatar.setLoginLocation(request.getLoginLocation());
		avatar.setUserId(request.getUserId());
		persistAvatar(avatar, false);
		loginAvatar(cluster, avatar);
		return avatar;
	}
	
	public void persistAvatar(ChatAvatar avatar, boolean async) {
		if(async)
			persister.execute(() -> persistAvatar(avatar));
		else
			persistAvatar(avatar);
	}
	
	private void persistAvatar(ChatAvatar avatar) {
		Output output = new Output(new ByteArrayOutputStream());
		kryos.get().writeClassAndObject(output, avatar);
		avatarDb.put(avatar.getAddressAndName().getBytes(), output.toBytes());
		output.close();
	}

	private void loginAvatar(ChatApiClient cluster, ChatAvatar avatar) {
		onlineAvatars.put(avatar.getAddressAndName(), avatar);
		avatar.setCluster(cluster);
		avatar.setLoggedIn(true);
		//TODO: add chat room + friends status updates etc.
		for(int mailId : avatar.getMailIds().toArray()) {
			PersistentMessage pm = getPersistentMessageFromDb(mailId);
			if(pm == null)
				continue;
		    int daysUntilDelete = config.getInt("deleteMailTimerInDays");
		    int elapsedDays = (int) TimeUnit.MILLISECONDS.toDays((System.currentTimeMillis() / 1000) - pm.getTimestamp());
		    if(elapsedDays >= daysUntilDelete) {
		    	destroyPersistentMessage(avatar, pm);
		    	continue;
		    }
			avatar.getPmList().put(mailId, pm);
		}
		if(!avatar.isInvisible()) {
			for(ChatAvatar avatar2 : onlineAvatars.values()) {
				if(avatar2.hasFriend(avatar.getAvatarId())) {
					MFriendLogin msg = new MFriendLogin();
					msg.setDestAvatarId(avatar2.getAvatarId());
					msg.setFriendAvatar(avatar);
					msg.setFriendAddress(avatar.getAddress());
					avatar2.getFriend(avatar.getAvatarId()).setStatus((short) 1);
					avatar2.getCluster().send(msg.serialize());
				}
			}
		}
		for(ChatFriend friend : avatar.getFriendsList()) {
			if(onlineAvatars.containsKey(friend.getFullAddress()))
				friend.setStatus((short) 1);
			else
				friend.setStatus((short) 0);
		}
		roomMessageIdMap.remove(avatar.getAvatarId());
		if(avatar.getName().getString().equalsIgnoreCase("system")) {
			createRootGameRoom(cluster, avatar);
			createRootClusterRoom(cluster, avatar);
		}
	}

	public ChatAvatar getAvatarFromDatabase(String fullAddress) {
		byte[] buf = avatarDb.get(fullAddress.getBytes());
		if(buf == null)
			return null;
		Input input = new Input(new ByteArrayInputStream(buf));
		ChatAvatar avatar = (ChatAvatar) kryos.get().readClassAndObject(input);
		input.close();
		if(avatar == null)
			return null;
		return avatar;
	}
	
	public ChatAvatar getAvatarById(int avatarId) {
		return onlineAvatars.values().stream().filter(avatar -> avatar.getAvatarId() == avatarId).findFirst().orElse(null);
	}

	public final Config getConfig() {
		return config;
	}

	public static ChatApiServer getInstance() {
		return instance;
	}

	public ChatApiTcpListener getRegistrar() {
		return registrar;
	}

	public void setRegistrar(ChatApiTcpListener registrar) {
		this.registrar = registrar;
	}

	public ChatApiTcpListener getGateway() {
		return gateway;
	}

	public void setGateway(ChatApiTcpListener gateway) {
		this.gateway = gateway;
	}

	public ExecutorService getPacketProcessor() {
		return packetProcessor;
	}

	public void setPacketProcessor(ExecutorService packetProcessor) {
		this.packetProcessor = packetProcessor;
	}

	public List<ChatApiClient> getConnectedClusters() {
		return connectedClusters;
	}

	public void setConnectedClusters(List<ChatApiClient> connectedClusters) {
		this.connectedClusters = connectedClusters;
	}

	public DB getAvatarDb() {
		return avatarDb;
	}

	public void setAvatarDb(DB avatarDb) {
		this.avatarDb = avatarDb;
	}

	public DB getChatRoomDb() {
		return chatRoomDb;
	}

	public void setChatRoomDb(DB chatRoomDb) {
		this.chatRoomDb = chatRoomDb;
	}

	public DB getMailDb() {
		return mailDb;
	}

	public void setMailDb(DB mailDb) {
		this.mailDb = mailDb;
	}
	
	public ChatApiClient getClusterByChannel(Channel channel) {
		for(ChatApiClient cluster : connectedClusters) {
			if(cluster.getChannel() == channel)
				return cluster;
		}
		return null;
	}
	
	public void addCluster(Channel channel) {
		ChatApiClient cluster = new ChatApiClient(channel);
		connectedClusters.add(cluster);
	}
	
	public void removeCluster(Channel channel) {
		connectedClusters.remove(getClusterByChannel(channel));
		// TODO: add disconnect handling
	}

	public Map<String, ChatAvatar> getOnlineAvatars() {
		return onlineAvatars;
	}

	public ExecutorService getPersister() {
		return persister;
	}

	public void setPersister(ExecutorService persister) {
		this.persister = persister;
	}

	public int getHighestAvatarId() {
		return highestAvatarId;
	}

	public void setHighestAvatarId(int highestAvatarId) {
		this.highestAvatarId = highestAvatarId;
	}

	public void handleLogoutAvatar(ChatApiClient cluster, RLogoutAvatar req) {
		ResLogoutAvatar res = new ResLogoutAvatar();
		res.setTrack(req.getTrack());
		ChatAvatar avatar = getAvatarById(req.getAvatarId());
		if(avatar == null) {
			res.setResult(ResponseResult.CHATRESULT_DESTAVATARDOESNTEXIST);
			cluster.send(res.serialize());
			return;
		}
		logoutAvatar(avatar, true);
		res.setResult(ResponseResult.CHATRESULT_SUCCESS);
		cluster.send(res.serialize());	
	}

	private void logoutAvatar(ChatAvatar avatar, boolean persist) {
		onlineAvatars.remove(avatar.getAddressAndName());
		avatar.setLoggedIn(false);
		if(persist)
			persistAvatar(avatar, true);
		//TODO: remove chat room + friends status updates etc.
		for(PersistentMessage pm : avatar.getPmList().valueCollection()) {
			persistPersistentMessage(pm, true);
		}
		for(ChatAvatar avatar2 : onlineAvatars.values()) {
			if(avatar2.hasFriend(avatar.getAvatarId())) {
				MFriendLogout msg = new MFriendLogout();
				msg.setDestAvatarId(avatar2.getAvatarId());
				msg.setFriendAvatar(avatar);
				msg.setFriendAddress(avatar.getAddress());
				avatar2.getFriend(avatar.getAvatarId()).setStatus((short) 0);
				avatar2.getCluster().send(msg.serialize());
			}
		}
		roomMessageIdMap.remove(avatar.getAvatarId());
	}
 
	private void persistPersistentMessage(PersistentMessage pm, boolean async) {
		if(async)
			persister.execute(() -> persistPersistentMessage(pm));
		else
			persistPersistentMessage(pm);
	}

	public void handleDestroyAvatar(ChatApiClient cluster, RDestroyAvatar req) {
		ResDestroyAvatar res = new ResDestroyAvatar();
		res.setTrack(req.getTrack());
		ChatAvatar avatar = getAvatarById(req.getAvatarId());
		if(avatar == null) {
			res.setResult(ResponseResult.CHATRESULT_DESTAVATARDOESNTEXIST);
			cluster.send(res.serialize());
			return;
		}
		destroyAvatar(avatar);
		res.setResult(ResponseResult.CHATRESULT_SUCCESS);
		cluster.send(res.serialize());		
	}
	
	public void destroyPersistentMessage(ChatAvatar avatar, PersistentMessage pm) {
		avatar.removeMail(pm);
		mailDb.delete(ByteBuffer.allocate(4).putInt(pm.getMessageId()).array());
	}

	private void destroyAvatar(ChatAvatar avatar) {
		//TODO: destroy mails, update friends/ignore lists, update chat rooms etc.
		logoutAvatar(avatar, false);
		avatarDb.delete(avatar.getAddressAndName().getBytes());
	}

	public void handleGetAnyAvatar(ChatApiClient cluster, RGetAnyAvatar req) {
		ResGetAnyAvatar res = new ResGetAnyAvatar();
		res.setTrack(req.getTrack());
		String fullAddress = req.getAddress() + "+" + req.getName();
		ChatAvatar avatar = onlineAvatars.get(fullAddress);
		if(avatar != null) {
			res.setResult(ResponseResult.CHATRESULT_SUCCESS);
			res.setAvatar(avatar);
			res.setLoggedIn(true);
			cluster.send(res.serialize());
			return;		
		}
		avatar = getAvatarFromDatabase(fullAddress);
		if(avatar != null) {
			res.setResult(ResponseResult.CHATRESULT_SUCCESS);
			res.setAvatar(avatar);
			res.setLoggedIn(false);
			cluster.send(res.serialize());
			return;					
		}
		res.setResult(ResponseResult.CHATRESULT_DESTAVATARDOESNTEXIST);
		res.setLoggedIn(false);
		cluster.send(res.serialize());
	}
	
	private int getNewPmId() {
		boolean found = false;
		int mailId = 0;
		while(!found) {
			mailId = ThreadLocalRandom.current().nextInt();
			if(mailId != 0 && getPersistentMessageFromDb(mailId) == null)
				found = true;
		}
		return mailId;
	}
	
	private PersistentMessage getPersistentMessageFromDb(int messageId) {
		byte[] key = ByteBuffer.allocate(4).putInt(messageId).array();
		byte[] value = mailDb.get(key);
		if(value == null)
			return null;
		Input input = new Input(new ByteArrayInputStream(value));
		PersistentMessage pm = (PersistentMessage) kryos.get().readClassAndObject(input);
		return pm;
	}
	
	private void persistPersistentMessage(PersistentMessage pm) {
		byte[] key = ByteBuffer.allocate(4).putInt(pm.getMessageId()).array();
		Output output = new Output(new ByteArrayOutputStream());
		kryos.get().writeClassAndObject(output, pm);
		mailDb.put(key, output.toBytes());
	}

	public void handleSendPersistentMessage(ChatApiClient cluster, RSendPersistentMessage req) {
		ResSendPersistentMessage res = new ResSendPersistentMessage();
		res.setTrack(req.getTrack());
		ChatAvatar srcAvatar = null;
		if(req.isAvatarPresence() != 0) {
			srcAvatar = getAvatarById(req.getSrcAvatarId());
		} else {
			srcAvatar = onlineAvatars.get(cluster.getAddress().getString() + "+" + req.getSrcName().getString());
		}
		if(srcAvatar == null) {
			res.setResult(ResponseResult.CHATRESULT_SRCAVATARDOESNTEXIST);
			cluster.send(res.serialize());
			return;					
		}
		String destFullAdress = req.getDestAddress().getString() + "+" + req.getDestName().getString();
		ChatAvatar destAvatar = onlineAvatars.get(destFullAdress);
		if(destAvatar == null) {
			destAvatar = getAvatarFromDatabase(destFullAdress);
			if(destAvatar == null) {
				res.setResult(ResponseResult.CHATRESULT_DESTAVATARDOESNTEXIST);
				cluster.send(res.serialize());
				return;					
			}
		}
		if(!srcAvatar.isGm() && !srcAvatar.isSuperGm() && destAvatar.hasIgnore(srcAvatar.getAvatarId())) {
			res.setResult(ResponseResult.CHATRESULT_IGNORING);
			cluster.send(res.serialize());
			return;					
		}
		PersistentMessage pm = new PersistentMessage();
		pm.setAvatarId(destAvatar.getAvatarId());
		pm.setMessage(req.getMessage());
		pm.setOob(req.getOob());
		pm.setMessageId(getNewPmId());
		pm.setCategory(req.getCategory());
		pm.setSenderAddress(srcAvatar.getAddress());
		pm.setSenderName(srcAvatar.getName());
		pm.setStatus(PersistentMessageStatus.NEW);
		pm.setSubject(req.getSubject());
		pm.setTimestamp((int) (System.currentTimeMillis() / 1000));
		persistPersistentMessage(pm);
		destAvatar.addMail(pm);
		System.out.println(req.getSubject().getString());
		System.out.println(req.getMessage().getString());
		System.out.println(req.getOob().getString());
		
		if(destAvatar.getCluster() != null && destAvatar.isLoggedIn()) {
			MPersistentMessage msg = new MPersistentMessage();
			msg.setPm(pm);
			destAvatar.getCluster().send(msg.serialize());
		}
		res.setResult(ResponseResult.CHATRESULT_SUCCESS);
		res.setMessageId(pm.getMessageId());
		cluster.send(res.serialize());
	}
	
	public ChatApiClient getClusterByAddress(ChatUnicodeString address) {
		for(ChatApiClient cluster : connectedClusters) {
			if(cluster.getAddress().getString().equals(address.getString()))
				return cluster;
		}
		return null;
	}

	public void handleAddFriend(ChatApiClient cluster, RAddFriend req) {
		ResAddFriend res = new ResAddFriend();
		res.setTrack(req.getTrack());
		ChatAvatar avatar = getAvatarById(req.getSrcAvatarId());
		System.out.println(req.getDestName().getString());
		if(avatar == null) {
			res.setResult(ResponseResult.CHATRESULT_SRCAVATARDOESNTEXIST);
			cluster.send(res.serialize());
			return;
		}
		if(avatar.hasFriend(req.getDestAddress(), req.getDestName())) {
			res.setResult(ResponseResult.CHATRESULT_DUPLICATEFRIEND);
			cluster.send(res.serialize());
			return;
		}
		String fullAddress = req.getDestAddress().getString() + "+" + req.getDestName().getString();
		ChatAvatar destAvatar = onlineAvatars.get(fullAddress);
		if(destAvatar == null) {
			destAvatar = getAvatarFromDatabase(fullAddress);
			if(destAvatar == null) {
				res.setResult(ResponseResult.CHATRESULT_DESTAVATARDOESNTEXIST);
				cluster.send(res.serialize());
				return;					
			}
		}
		if(destAvatar == avatar) {
			res.setResult(ResponseResult.CHATRESULT_INVALID_INPUT);
			cluster.send(res.serialize());
			return;
		}
		ChatFriend friend = new ChatFriend();
		friend.setAvatarId(destAvatar.getAvatarId());
		friend.setAddress(destAvatar.getAddress());
		friend.setName(destAvatar.getName());
		friend.setComment(req.getComment());
		friend.setStatus((short) (destAvatar.isLoggedIn() ? 1 : 0));
		avatar.addFriend(friend);
		res.setResult(ResponseResult.CHATRESULT_SUCCESS);
		cluster.send(res.serialize());
		if(friend.getStatus() != 0) {
			MFriendLogin msg = new MFriendLogin();
			msg.setDestAvatarId(avatar.getAvatarId());
			msg.setFriendAvatar(destAvatar);
			msg.setFriendAddress(destAvatar.getAddress());
			cluster.send(msg.serialize());
		}
	}

	public void handleRemoveFriend(ChatApiClient cluster, RRemoveFriend req) {
		ResRemoveFriend res = new ResRemoveFriend();
		res.setTrack(req.getTrack());
		ChatAvatar avatar = getAvatarById(req.getSrcAvatarId());
		if(avatar == null) {
			res.setResult(ResponseResult.CHATRESULT_SRCAVATARDOESNTEXIST);
			cluster.send(res.serialize());
			return;
		}
		if(!avatar.hasFriend(req.getDestAddress(), req.getDestName())) {
			res.setResult(ResponseResult.CHATRESULT_FRIENDNOTFOUND);
			cluster.send(res.serialize());
			return;
		}
		avatar.removeFriend(req.getDestAddress(), req.getDestName());
		res.setResult(ResponseResult.CHATRESULT_SUCCESS);
		cluster.send(res.serialize());
	}

	public void handleAddIgnore(ChatApiClient cluster, RAddIgnore req) {
		ResAddIgnore res = new ResAddIgnore();
		res.setTrack(req.getTrack());
		ChatAvatar avatar = getAvatarById(req.getSrcAvatarId());
		if(avatar == null) {
			res.setResult(ResponseResult.CHATRESULT_SRCAVATARDOESNTEXIST);
			cluster.send(res.serialize());
			return;
		}
		if(avatar.hasIgnore(req.getDestAddress(), req.getDestName())) {
			res.setResult(ResponseResult.CHATRESULT_DUPLICATEIGNORE);
			cluster.send(res.serialize());
			return;
		}
		String fullAddress = req.getDestAddress().getString() + "+" + req.getDestName().getString();
		System.out.println(fullAddress);
		ChatAvatar destAvatar = onlineAvatars.get(fullAddress);
		if(destAvatar == null) {
			destAvatar = getAvatarFromDatabase(fullAddress);
			if(destAvatar == null) {
				res.setResult(ResponseResult.CHATRESULT_DESTAVATARDOESNTEXIST);
				cluster.send(res.serialize());
				return;					
			}
		}
		if(destAvatar == avatar) {
			res.setResult(ResponseResult.CHATRESULT_INVALID_INPUT);
			cluster.send(res.serialize());
			return;
		}
		ChatIgnore ignore = new ChatIgnore();
		ignore.setAvatarId(destAvatar.getAvatarId());
		ignore.setAddress(destAvatar.getAddress());
		ignore.setName(destAvatar.getName());
		avatar.addIgnore(ignore);
		res.setResult(ResponseResult.CHATRESULT_SUCCESS);
		cluster.send(res.serialize());
	}

	public void handleRemoveIgnore(ChatApiClient cluster, RRemoveIgnore req) {
		ResRemoveIgnore res = new ResRemoveIgnore();
		res.setTrack(req.getTrack());
		ChatAvatar avatar = getAvatarById(req.getSrcAvatarId());
		if(avatar == null) {
			res.setResult(ResponseResult.CHATRESULT_SRCAVATARDOESNTEXIST);
			cluster.send(res.serialize());
			return;
		}
		if(!avatar.hasIgnore(req.getDestAddress(), req.getDestName())) {
			res.setResult(ResponseResult.CHATRESULT_IGNORENOTFOUND);
			cluster.send(res.serialize());
			return;
		}
		avatar.removeIgnore(req.getDestAddress(), req.getDestName());
		res.setResult(ResponseResult.CHATRESULT_SUCCESS);
		cluster.send(res.serialize());
	}

	public TIntIntMap getRoomMessageIdMap() {
		return roomMessageIdMap;
	}

	public void setRoomMessageIdMap(TIntIntMap roomMessageIdMap) {
		this.roomMessageIdMap = roomMessageIdMap;
	}

	public void handleGetRoomSummaries(ChatApiClient cluster, RGetRoomSummaries req) {
		ResGetRoomSummaries res = new ResGetRoomSummaries();
		res.setTrack(req.getTrack());
		List<ChatRoom> rooms = new ArrayList<>();
		for(ChatRoom room : roomMap.values()) {
			//if(room.getRoomAddress().getString().startsWith(req.getStartNodeAddress().getString()) && room.getRoomAddress().getString().contains(req.getRoomFilter().getString()))
			rooms.add(room);
			//System.out.println(room.getFullAddress());
		}
		res.setRooms(rooms);
		res.setResult(ResponseResult.CHATRESULT_SUCCESS);
		cluster.send(res.serialize());
	}

	public void handleCreateRoom(ChatApiClient cluster, RCreateRoom req) {
		ResCreateRoom res = new ResCreateRoom();
		res.setTrack(req.getTrack());
		ChatAvatar avatar = getAvatarById(req.getSrcAvatarId());
		if(avatar == null) {
			res.setResult(ResponseResult.CHATRESULT_SRCAVATARDOESNTEXIST);
			cluster.send(res.serialize());
			return;
		}
		if(roomMap.get(req.getRoomAddress().getString() + "+" + req.getRoomName().getString()) != null) {
			//System.out.println("room already exists");
			res.setResult(ResponseResult.CHATRESULT_ROOM_ALREADYEXISTS);
			cluster.send(res.serialize());
			return;
		}
		
		ChatRoom room = new ChatRoom();
		room.setCreateTime((int) (System.currentTimeMillis() / 1000));
		room.setRoomId(getNewRoomId());
		room.setCreatorId(avatar.getAvatarId());
		room.setCreatorAddress(avatar.getAddress());
		room.setCreatorName(avatar.getName());
		room.setRoomAddress(new ChatUnicodeString(req.getRoomAddress().getString() + "+" + req.getRoomName().getString()));
		room.setRoomAttributes(req.getRoomAttributes());
		room.setRoomName(req.getRoomName());
		room.setRoomPassword(req.getRoomPassword());
		room.setRoomTopic(req.getRoomTopic());
		room.setNodeLevel(room.getRoomAddress().getString().split("\\+").length);
		room.setMaxRoomSize(req.getMaxRoomSize());
		room.addAvatar(avatar);
		room.addAdmin(avatar);
		/*System.out.println(req.getRoomAttributes());
		System.out.println(req.getRoomAddress().getString());
		System.out.println(req.getRoomName().getString());
		System.out.println(req.getRoomTopic().getString());
		System.out.println(req.getMaxRoomSize());*/
		roomMap.put(room.getRoomAddress().getString(), room);
		res.setResult(ResponseResult.CHATRESULT_SUCCESS);
		res.setRoom(room);
		List<ChatRoom> parentRooms = getParentRooms(room);
		res.setExtraRooms(parentRooms);
		cluster.send(res.serialize());
	}

	public void handleGetRoom(ChatApiClient cluster, RGetRoom req) {
		ResGetRoom res = new ResGetRoom();
		res.setTrack(req.getTrack());
		ChatRoom room = roomMap.get(req.getRoomAddress().getString());
		System.out.println("GetRoom for " + req.getRoomAddress().getString());
		if(room == null) {
			res.setResult(ResponseResult.CHATRESULT_ADDRESSDOESNTEXIST);
			cluster.send(res.serialize());
			return;
		}
		res.setRoom(room);
		List<ChatRoom> parentRooms = getParentRooms(room);
		//List<ChatRoom> subRooms = getSubRooms(room);
		res.setExtraRooms(parentRooms);
		res.setResult(ResponseResult.CHATRESULT_SUCCESS);
		cluster.send(res.serialize());
	}

	private List<ChatRoom> getParentRooms(ChatRoom room) {
		List<ChatRoom> parentRooms = new ArrayList<>();
		for(ChatRoom parent : roomMap.values()) {
			if(room.getRoomAddress().getString().startsWith(parent.getRoomAddress().getString()))
				parentRooms.add(parent);
		}
		return parentRooms;
	}
	
	private List<ChatRoom> getSubRooms(ChatRoom room) {
		List<ChatRoom> subRooms = new ArrayList<>();
		for(ChatRoom sub : roomMap.values()) {
			if(sub.getRoomAddress().getString().startsWith(room.getRoomAddress().getString()))
				subRooms.add(sub);
		}
		return subRooms;
	}

	public void handleEnterRoom(ChatApiClient cluster, REnterRoom req) {
		ResEnterRoom res = new ResEnterRoom() ;
		res.setTrack(req.getTrack());
		System.out.println(req.getRoomAddress().getString());
		ChatAvatar avatar = getAvatarById(req.getSrcAvatarId());
		if(avatar == null) {
			res.setResult(ResponseResult.CHATRESULT_SRCAVATARDOESNTEXIST);
			cluster.send(res.serialize());
			return;
		}
		ChatRoom room = roomMap.get(req.getRoomAddress().getString());
		if(room == null) {
			res.setResult(ResponseResult.CHATRESULT_ADDRESSDOESNTEXIST);
			cluster.send(res.serialize());
			return;
		}
		System.out.println("entering room");

		res.setRoomId(room.getRoomId());
		room.addAvatar(avatar);
		res.setGotRoom(true);
		res.setRoom(room);
		res.setResult(ResponseResult.CHATRESULT_SUCCESS);
		cluster.send(res.serialize());		
	}

	public void handleLeaveRoom(ChatApiClient cluster, RGetRoomSummaries req) {
		
	}

	public Map<String, ChatRoom> getRoomMap() {
		return roomMap;
	}

	public void setRoomMap(Map<String, ChatRoom> roomMap) {
		this.roomMap = roomMap;
	}
	
	public ChatRoom getRoomById(int roomId) {
		for(ChatRoom room : roomMap.values()) {
			if(room.getRoomId() == roomId) {
				return room;
			}
		}
		return null;
	}
	
	private int getNewRoomId() {
		boolean found = false;
		int roomId = 0;
		while(!found) {
			roomId = ThreadLocalRandom.current().nextInt();
			if(roomId != 0 && getRoomById(roomId) == null)
				found = true;
		}
		return roomId;
	}
	
	private void persistChatRoom(ChatRoom room) {
		Output output = new Output(new ByteArrayOutputStream());
		kryos.get().writeClassAndObject(output, room);
		byte[] value = output.toBytes();
		byte[] key = room.getRoomAddress().getString().getBytes();
		chatRoomDb.put(key, value);
	}

	public ScheduledExecutorService getScheduler() {
		return scheduler;
	}

	public void setScheduler(ScheduledExecutorService scheduler) {
		this.scheduler = scheduler;
	}

	public void handleSendRoomMessage(ChatApiClient cluster, RSendRoomMessage req) {
		ResSendRoomMessage res = new ResSendRoomMessage() ;
		res.setTrack(req.getTrack());
		System.out.println(req.getRoomAddress().getString());
		ChatAvatar avatar = getAvatarById(req.getSrcAvatarId());
		if(avatar == null) {
			res.setResult(ResponseResult.CHATRESULT_SRCAVATARDOESNTEXIST);
			cluster.send(res.serialize());
			return;
		}
		ChatRoom room = roomMap.get(req.getRoomAddress().getString());
		if(room == null) {
			res.setResult(ResponseResult.CHATRESULT_ADDRESSDOESNTEXIST);
			cluster.send(res.serialize());
			return;
		}
		System.out.println("sending room msg");
		res.setDestRoomId(room.getRoomId());
		res.setResult(ResponseResult.CHATRESULT_SUCCESS);
		cluster.send(res.serialize());		
		MRoomMessage msg = new MRoomMessage();
		TIntArrayList destAvatarIdList = new TIntArrayList();
		room.getAvatarList().stream().map(ChatAvatar::getAvatarId).forEach(destAvatarIdList::add);
		msg.setDestAvatarIdList(destAvatarIdList);
		msg.setMessage(req.getMsg());
		msg.setOob(req.getOob());
		msg.setRoomId(room.getRoomId());
		msg.setMessageId(getNewRoomMsgId(avatar));
		msg.setAvatar(avatar);
		cluster.send(msg.serialize());
	}

	private int getNewRoomMsgId(ChatAvatar avatar) {
		boolean found = false;
		int msgId = 0;
		while(!found) {
			msgId = ThreadLocalRandom.current().nextInt();
			if(msgId != 0 && roomMessageIdMap.get(avatar.getAvatarId()) != msgId)
				found = true;
		}
		roomMessageIdMap.put(avatar.getAvatarId(), msgId);
		return msgId;
	}
	
	private void createRootClusterRoom(ChatApiClient cluster, ChatAvatar systemAvatar) {
		String clusterFullAddress = cluster.getAddress().getString();
		String[] splitAddress = clusterFullAddress.split("\\+");
		ChatRoom room = new ChatRoom();
		room.setCreateTime((int) (System.currentTimeMillis() / 1000));
		room.setRoomId(getNewRoomId());
		room.setCreatorId(systemAvatar.getAvatarId());
		room.setCreatorAddress(systemAvatar.getAddress());
		room.setCreatorName(systemAvatar.getName());
		room.setRoomAddress(new ChatUnicodeString(splitAddress[0] + "+" + splitAddress[1] + "+" + splitAddress[2]));
		room.setRoomAttributes(0);
		room.setRoomName(new ChatUnicodeString(splitAddress[2]));
		room.setRoomPassword(new ChatUnicodeString());
		room.setRoomTopic(new ChatUnicodeString());
		room.setMaxRoomSize(0);
		room.addAvatar(systemAvatar);
		room.addAdmin(systemAvatar);
		roomMap.put(room.getRoomAddress().getString(), room);
	}
	
	private void createRootGameRoom(ChatApiClient cluster, ChatAvatar systemAvatar) {
		String clusterFullAddress = cluster.getAddress().getString();
		String[] splitAddress = clusterFullAddress.split("\\+");
		ChatRoom room = new ChatRoom();
		room.setCreateTime((int) (System.currentTimeMillis() / 1000));
		room.setRoomId(getNewRoomId());
		room.setCreatorId(systemAvatar.getAvatarId());
		room.setCreatorAddress(systemAvatar.getAddress());
		room.setCreatorName(systemAvatar.getName());
		room.setRoomAddress(new ChatUnicodeString(splitAddress[0] + "+" + splitAddress[1]));
		room.setRoomAttributes(0);
		room.setRoomName(new ChatUnicodeString(splitAddress[1]));
		room.setRoomPassword(new ChatUnicodeString());
		room.setRoomTopic(new ChatUnicodeString());
		room.setMaxRoomSize(0);
		room.addAvatar(systemAvatar);
		room.addAdmin(systemAvatar);
		roomMap.put(room.getRoomAddress().getString(), room);
	}

}
