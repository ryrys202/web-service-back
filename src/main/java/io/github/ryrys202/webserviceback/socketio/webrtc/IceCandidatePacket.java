package io.github.ryrys202.webserviceback.socketio.webrtc;

import com.fasterxml.jackson.databind.JsonNode;

public record IceCandidatePacket(String roomId, String iceFromId, JsonNode iceCandidateBody) {
}
