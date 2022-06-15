package io.github.ryrys202.webserviceback.socketio.chat;

public record IncomingChatMessagePacket(String roomId, String fromId, String name, String message) {
}
