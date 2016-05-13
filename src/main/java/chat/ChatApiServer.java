package chat;

import chat.util.Config;

public class ChatApiServer {
	
	private final Config clusterList;
	private final Config config;
	private static ChatApiServer instance;
	private ChatApiTcpListener registrar;
	private ChatApiTcpListener gateway;
	
	public ChatApiServer() {
		instance = this;
		clusterList = new Config("clusterList.cfg");
		config = new Config("config.cfg");
		
	}
	
	public static void main(String[] args) {
		
		System.setProperty("Log4jContextSelector", "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");
		
	}

	public final Config getConfig() {
		return config;
	}

	public final Config getClusterList() {
		return clusterList;
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

}
