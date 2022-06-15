package io.github.ryrys202.webserviceback.socketio.admin;

public record KickUserPacket(String roomId, String userId, String adminSecret, String userToKick) {
}
