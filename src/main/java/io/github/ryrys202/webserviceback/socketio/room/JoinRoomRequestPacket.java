package io.github.ryrys202.webserviceback.socketio.room;

public record JoinRoomRequestPacket(String roomId, String userId, String name, boolean useVideo, boolean useAudio) {
}
