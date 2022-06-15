package io.github.ryrys202.webserviceback.util;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import io.github.ryrys202.webserviceback.WebServiceBack;
import io.github.kynmarsher.webserviceback.datamodel.RoomMember;
import io.socket.socketio.server.SocketIoNamespace;
import io.socket.socketio.server.SocketIoSocket;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.Optional;

@UtilityClass
public class Utils {
    private static char[] ID_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();

    @SneakyThrows
    public static String dataToJson(Object data) {
        StringWriter sw = new StringWriter();
        WebServiceBack.RESPONSE_MAPPER.writeValue(sw, data);
        return sw.toString();
    }

    public static String roomId() {
        return NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, ID_ALPHABET, 6);
    }

    public static String userId() {
        return NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, ID_ALPHABET, 8);
    }

    public static String adminSecret() {
        return NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, ID_ALPHABET, 128);
    }

    public static Optional<SocketIoSocket> userBySocketId(SocketIoNamespace namespace, String roomId, RoomMember member) {

        return Arrays.stream(namespace.getAdapter().listClients(roomId))
                .filter(client -> client.getId().equals(member.socketId()))
                .reduce((a, b) -> null);
    }

}
