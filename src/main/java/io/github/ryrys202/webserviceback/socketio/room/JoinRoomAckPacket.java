package io.github.ryrys202.webserviceback.socketio.room;

public record JoinRoomAckPacket(boolean exists, String userId, boolean isAdmin, String adminSecret) {
}
