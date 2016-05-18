package chat;

import io.netty.channel.Channel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

import chat.protocol.message.MSendInstantMessage;
import chat.protocol.request.RDestroyAvatar;
import chat.protocol.request.RGetAnyAvatar;
import chat.protocol.request.RLoginAvatar;
import chat.protocol.request.RLogoutAvatar;
import chat.protocol.request.RSendInstantMessage;
import chat.protocol.response.ResDestroyAvatar;
import chat.protocol.response.ResGetAnyAvatar;
import chat.protocol.response.ResLoginAvatar;
import chat.protocol.response.ResLogoutAvatar;
import chat.protocol.response.ResSendInstantMessage;
import chat.protocol.response.ResponseResult;
import chat.util.Config;

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
		if(destAvatar == null) {
			res.setResult(ResponseResult.CHATRESULT_DESTAVATARDOESNTEXIST);
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

}
