package io.github.ryrys202.webserviceback.datamodel;

import io.github.ryrys202.webserviceback.util.Utils;

import java.util.*;

public class Room {
    private final String roomId;
    private String adminId = null;
    private String adminSecret = null;
    private final Map<String, RoomMember> memberList;

    public Room() {
        roomId = Utils.roomId();
        memberList = new HashMap<>();
    }

    public String roomId() {
        return roomId;
    }

    public String adminId() {
        return adminId;
    }

    public void addMember(RoomMember member) {
        memberList.put(member.userId(), member);
    }

    public RoomMember getMember(String memberId) {
        return memberList.get(memberId);
    }

    public boolean isMember(String memberId) {
        return memberList.containsKey(memberId);
    }

    public boolean isAdmin(String userId) {
        return adminId.equals(userId);
    }

    public boolean isAdminClaimable() {
        return adminId == null;
    }

    public boolean checkAdminPerms(String passedSecret) {
        return adminSecret.equals(passedSecret);
    }

    public String claimAdmin(String userId) {
        adminId = userId;
        adminSecret = Utils.adminSecret();
        return adminSecret;
    }
}
