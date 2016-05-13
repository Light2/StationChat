package chat.protocol.response;

import java.nio.ByteOrder;

import io.netty.buffer.ByteBuf;
import chat.protocol.GenericResponse;

public class ResLoginAvatar extends GenericResponse {

	public ResLoginAvatar(short type) {
		super(type);
	}

	@Override
	public ByteBuf serialize() {
		ByteBuf buf = alloc.buffer().order(ByteOrder.LITTLE_ENDIAN);
		return null;
	}

	@Override
	public void deserialize(ByteBuf buf) {
		
	}
	
	

}
