package chat.protocol.response;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import io.netty.buffer.ByteBuf;
import chat.protocol.GenericResponse;

public class ResLoginAvatar extends GenericResponse {


	@Override
	public ByteBuffer serialize() {
		ByteBuf buf = alloc.heapBuffer().order(ByteOrder.LITTLE_ENDIAN);
		return null;
	}

	@Override
	public void deserialize(ByteBuffer buf) {
		
	}
	
	

}
