package chat.protocol.response;

import java.nio.ByteBuffer;

import chat.protocol.GenericResponse;

public class ResGetRoomSummaries extends GenericResponse {
	
	public ResGetRoomSummaries() {
		type = RESPONSE_GETROOMSUMMARIES;
	}

	@Override
	public ByteBuffer serialize() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void deserialize(ByteBuffer buf) {
		// TODO Auto-generated method stub
		
	}

}
