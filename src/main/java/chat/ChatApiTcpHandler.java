package chat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;



import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;



import chat.protocol.GenericRequest;
import chat.protocol.request.RRegistrarGetChatServer;
import chat.protocol.request.RSendApiVersion;
import chat.protocol.response.ResRegistrarGetChatServer;
import chat.protocol.response.ResSendApiVersion;
import chat.protocol.response.ResponseResult;
import chat.util.ChatUnicodeString;
import gnu.trove.map.TShortObjectMap;
import gnu.trove.map.hash.TShortObjectHashMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
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
    		cluster.send(res.serialize());
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
	}

	@Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
		ChatApiClient cluster = server.getClusterByChannel(ctx.channel());
		// msg comes in as an unpooled unsafe buffer in native memory
		ByteBuf unsafe = (ByteBuf) msg;
    	ByteBuffer packet = ByteBuffer.allocate(((ByteBuf) msg).readableBytes()).order(ByteOrder.LITTLE_ENDIAN);
		//System.out.println(((ByteBuf) msg).readableBytes());
    	unsafe.getBytes(0, packet);
    	packet.position(0);
    	//System.out.println(bytesToHex(packet.array()));
    	if(packet.capacity() < 6) {
    		logger.warn("Recieved packet of size < 6 bytes");
    		System.out.println("test3");
    		//System.out.println(bytesToHex(packet.array()));
			return;
    	}
    	if(cluster == null) {
			logger.warn("ChatApiClient object not found for given channel");
			return;
		}
    	packet.getInt(); // probably length of packet
    	short type = packet.getShort(4);
    	PacketHandler handler = packetTypes.get(type);
    	if(handler == null) {
    		logger.info("Unhandled packet type: {}", type);
    		System.out.println(bytesToHex(packet.array()));
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
		System.out.println("test5");

		server.removeCluster(ctx.channel());
    }
    
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
		System.out.println("test");

		server.addCluster(ctx.channel());
    }

}
