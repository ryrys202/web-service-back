package io.github.ryrys202.webserviceback.datamodel;

public record RoomMember(String name, String userId, String socketId, boolean video, boolean audio) {

    public RoomMember(String name, String socketId, boolean video, boolean audio) {
        this(name, Utils.userId(), socketId, video, audio);
    }
}
