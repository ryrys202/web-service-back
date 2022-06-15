package io.github.ryrys202.webserviceback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.ryrys202.webserviceback.datamodel.Room;
import io.github.kynmarsher.webserviceback.datamodel.RoomMember;
import io.github.kynmarsher.webserviceback.socketio.admin.KickUserPacket;
import io.github.kynmarsher.webserviceback.socketio.admin.NotifyKickPacket;
import io.github.kynmarsher.webserviceback.socketio.chat.IncomingChatMessagePacket;
import io.github.kynmarsher.webserviceback.socketio.room.*;
import io.github.kynmarsher.webserviceback.socketio.webrtc.*;
import io.github.ryrys202.webserviceback.util.Utils;
import io.socket.engineio.server.EngineIoServer;
import io.socket.engineio.server.EngineIoServerOptions;
import io.socket.engineio.server.JettyWebSocketHandler;
import io.socket.socketio.server.SocketIoServer;
import io.socket.socketio.server.SocketIoSocket;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.http.pathmap.ServletPathSpec;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeFilter;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;

import static io.github.ryrys202.webserviceback.util.Utils.dataToJson;

@Slf4j
@Accessors(fluent = true)
public class WebServiceBack {
    // Эти поля - мапперы которые конвертируют объект в JSON и обратно в объект
    public static ObjectMapper RESPONSE_MAPPER;
    public static ObjectMapper STRICT_MAPPER;
    @Getter
    public Map<String, Room> roomList;

    private final EngineIoServer mEngineIoServer;
    private final EngineIoServerOptions eioOptions;
    private @Getter final SocketIoServer mSocketIoServer;
    private final Server mServer;

    public static WebServiceBack INSTANCE;


    public WebServiceBack(String[] allowedCorsOrigins) {
        INSTANCE = this;

        STRICT_MAPPER = new ObjectMapper();
        RESPONSE_MAPPER = new ObjectMapper();
        RESPONSE_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);

        roomList = new HashMap<>();
        // Jetty and Socket.io init
        this.mServer = new Server(new InetSocketAddress(3200));
        eioOptions = EngineIoServerOptions.newFromDefault();
        eioOptions.setAllowedCorsOrigins(allowedCorsOrigins);
        this.mEngineIoServer = new EngineIoServer(eioOptions);
        this.mSocketIoServer = new SocketIoServer(mEngineIoServer);

        System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog");
        System.setProperty("org.eclipse.jetty.LEVEL", "OFF");

        final var servletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        servletContextHandler.setContextPath("/");
        servletContextHandler.addFilter(RemoteAddrFilter.class, "/socket.io/*", EnumSet.of(DispatcherType.REQUEST));


        servletContextHandler.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
                mEngineIoServer.handleRequest(new HttpServletRequestWrapper(request) {
                    @Override
                    public boolean isAsyncSupported() {
                        return false;
                    }
                }, response);
            }
        }), "/socket.io/*");

        try {
            WebSocketUpgradeFilter webSocketUpgradeFilter = WebSocketUpgradeFilter.configureContext(servletContextHandler);
            webSocketUpgradeFilter.addMapping(
                    new ServletPathSpec("/socket.io/*"),
                    (servletUpgradeRequest, servletUpgradeResponse) -> new JettyWebSocketHandler(mEngineIoServer));
        } catch (ServletException ex) {
            ex.printStackTrace();
        }

        HandlerList handlerList = new HandlerList();
        handlerList.setHandlers(new Handler[] { servletContextHandler });
        mServer.setHandler(handlerList);
    }

    public void startServer() throws Exception {
        mServer.start();
    }

    public void stopServer() throws Exception {
        mServer.stop();
    }

    public void initializeListeners() {
        final var mainNamespace = mSocketIoServer.namespace("/");
        mainNamespace.on("connection", arguments -> {
            SocketIoSocket socket = (SocketIoSocket) arguments[0];
            log.info("[Socket %s] connected".formatted(socket.getId()));

            socket.on("createRoom", msgArgs -> {
                try {
                    // final var createRoomRequest = WebServiceBack.STRICT_MAPPER.readValue(msgArgs[0].toString(), CreateRoomRequestPacket.class);
                    log.info("[Socket %s] requested room creation".formatted(socket.getId()));
                    final var newRoom = new Room();

                    roomList.put(newRoom.roomId(), newRoom);

                    final var createRoomAck = new CreateRoomAckPacket(newRoom.roomId());

                    if (msgArgs[msgArgs.length - 1] instanceof SocketIoSocket.ReceivedByLocalAcknowledgementCallback callback) {
                        callback.sendAcknowledgement(dataToJson(createRoomAck));
                        log.info("[Socket %s] Created room: %s".formatted(socket.getId(), createRoomAck.roomId()));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            socket.on("joinRoom", msgArgs -> {
                try {
                    final var joinRoomRequest = WebServiceBack.STRICT_MAPPER.readValue(msgArgs[0].toString(), JoinRoomRequestPacket.class);
                    log.info("[Socket %s] requested to join the room %s".formatted(socket.getId(), joinRoomRequest.roomId()));
                    // ACK ответ на случай если комнаты не существует (Пользователь не администратор)
                    var joinRoomAck = new JoinRoomAckPacket(false, "none", false, "");

                    // Комната существует
                    if (roomList.containsKey(joinRoomRequest.roomId())) {
                        // Создаем нового пользователя
                       RoomMember member;
                       final var currentRoom = roomList.get(joinRoomRequest.roomId());
                        if (currentRoom.isMember(joinRoomRequest.userId())) {
                            // Пользователь уже в комнате
                            log.info("[Socket %s] User that already exists joins the room. ID: %s".formatted(socket.getId(), joinRoomRequest.userId()));
                            member = roomList.get(joinRoomRequest.roomId()).getMember(joinRoomRequest.userId());
                        } else {
                            member = new RoomMember(joinRoomRequest.name(),
                                    socket.getId(),
                                    joinRoomRequest.useVideo(),
                                    joinRoomRequest.useAudio());
                            // Комната существует но пользователь не администратор
                            joinRoomAck = new JoinRoomAckPacket(true,
                                    member.userId(),
                                    false,
                                    "");

                            // Стать администратором
                            if (currentRoom.isAdminClaimable()) {
                                final var secret = currentRoom.claimAdmin(member.userId());
                                joinRoomAck = new JoinRoomAckPacket(true,
                                        member.userId(),
                                        true,
                                        secret);
                            }
                            currentRoom.addMember(member);
                            log.info("[Socket %s] Created new user. ID: %s".formatted(socket.getId(), member.userId()));
                        }
                    } else {
                        log.info("[Socket %s] tried non existent room %s".formatted(socket.getId(), joinRoomRequest.roomId()));
                    }

                    if (msgArgs[msgArgs.length - 1] instanceof SocketIoSocket.ReceivedByLocalAcknowledgementCallback callback) {
                        callback.sendAcknowledgement(dataToJson(joinRoomAck));
                        log.info("[Socket %s] joined the room %s".formatted(socket.getId(), joinRoomRequest.roomId()));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            socket.on("startCall", msgArgs -> {
                try {
                    final var startCall = WebServiceBack.STRICT_MAPPER.readValue(msgArgs[0].toString(), StartCallPacket.class);
                    log.info("[Socket %s] Starting calling recipients in room: %s".formatted(socket.getId(), startCall.roomId()));
                    // Присоединяем его сокет к комнате как и самого пользователя
                    socket.joinRoom(startCall.roomId());
                    socket.broadcast(startCall.roomId(), "startCall", msgArgs);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            });

            // WebRTC
            socket.on("createOffer", msgArgs -> {
                try {
                    final var offerPacket = WebServiceBack.STRICT_MAPPER.readValue(msgArgs[0].toString(), CreateOfferPacket.class);

                    log.info("[Socket %s] [Room %s] %s sent offer to client %s".formatted(socket.getId(), offerPacket.roomId(), offerPacket.offerFromId(), offerPacket.offerToId()));

                    // Найдем айди его сокета по неймспейсу, answerPacket, RoomMember объекту
                    Utils.userBySocketId(mainNamespace, offerPacket.roomId(), roomList.get(offerPacket.roomId()).getMember(offerPacket.offerToId()))
                            .ifPresentOrElse(
                                    foundSocket -> foundSocket.send("createOffer", msgArgs[0]),
                                    () -> userNotFoundWarn(socket, offerPacket.offerToId(), "createOffer"));

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            // WebRTC
            socket.on("answerOffer", msgArgs -> {
                try {
                    final var answerPacket = WebServiceBack.STRICT_MAPPER.readValue(msgArgs[0].toString(), AnswerOfferPacket.class);

                    log.info("[Socket %s] [Room %s] User %s sent answer to %s".formatted(socket.getId(), answerPacket.roomId(), answerPacket.answerFromId(), answerPacket.answerToId()));

                    // Найдем айди его сокета по неймспейсу, answerPacket, RoomMember объекту
                    Utils.userBySocketId(mainNamespace, answerPacket.roomId(), roomList.get(answerPacket.roomId()).getMember(answerPacket.answerToId()))
                            .ifPresentOrElse(
                                    foundSocket -> foundSocket.send("answerOffer", msgArgs[0]),
                                    () -> userNotFoundWarn(socket, answerPacket.answerToId(), "answerOffer"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            // WebRTC
            socket.on("iceCandidate", msgArgs -> {
                try {
                    final var iceCandidatePacket = WebServiceBack.STRICT_MAPPER.readValue(msgArgs[0].toString(), IceCandidatePacket.class);
                    socket.broadcast(iceCandidatePacket.roomId(), "iceCandidate", msgArgs[0]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            socket.on("chatMessage", msgArgs -> {
                try {
                    final var chatMsg = WebServiceBack.STRICT_MAPPER.readValue(msgArgs[0].toString(), IncomingChatMessagePacket.class);
                    var responseObj = new GenericAnswerPacket(true, "Success");

                    if ( chatMsg.message() != null && chatMsg.message().length() <= 128 ) {
                        socket.broadcast(chatMsg.roomId(), "chatMessage", msgArgs[0]);
                    } else {
                        responseObj = new GenericAnswerPacket(false, "Message is too big");
                    }

                    if (msgArgs[msgArgs.length - 1] instanceof SocketIoSocket.ReceivedByLocalAcknowledgementCallback callback) {
                        callback.sendAcknowledgement(dataToJson(responseObj));
                        log.info("[Client %s] Room %s msg:  %s".formatted(socket.getId(), chatMsg.roomId(), chatMsg.message()));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            socket.on("kickUser", msgArgs -> {
                try {
                    final var kickUserPacket = WebServiceBack.STRICT_MAPPER.readValue(msgArgs[0].toString(), KickUserPacket.class);
                    var kickUserAck = new GenericAnswerPacket(false, "No such user");

                    if (roomList.get(kickUserPacket.roomId()).checkAdminPerms(kickUserPacket.adminSecret())) {
                        kickUserAck = new GenericAnswerPacket(true, "User is kicked");
                        Utils.userBySocketId(mainNamespace, kickUserPacket.roomId(), roomList.get(kickUserPacket.roomId()).getMember(kickUserPacket.userToKick()))
                                .ifPresentOrElse(
                                        foundSocket -> foundSocket.send("kickUser", dataToJson(new NotifyKickPacket(kickUserPacket.userToKick()))),
                                        () -> userNotFoundWarn(socket, kickUserPacket.userToKick(), "kickUser"));
                    }
                    if (msgArgs[msgArgs.length - 1] instanceof SocketIoSocket.ReceivedByLocalAcknowledgementCallback callback) {
                        callback.sendAcknowledgement(dataToJson(kickUserAck));
                    }
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            });
        });
    }

    private static void userNotFoundWarn(SocketIoSocket socket, String userId, String eventDescription) {
        log.warn("[Socket %s] No socket for requested user %s on %s".formatted(socket.getId(), userId, eventDescription));
    }
}
