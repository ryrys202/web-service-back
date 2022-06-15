package io.github.ryrys202.webserviceback.socketio.room;

public record StartCallPacket(String roomId, String userId, String name) {
}
