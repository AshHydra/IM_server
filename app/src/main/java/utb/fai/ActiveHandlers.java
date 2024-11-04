package utb.fai;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveHandlers {
    private ConcurrentHashMap<String, SocketHandler> activeHandlersMap = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, Set<SocketHandler>> chatRooms = new ConcurrentHashMap<>();

    synchronized void sendMessageToRoom(String roomName, SocketHandler sender, String message) {
        Set<SocketHandler> room = chatRooms.get(roomName);
        if (room != null) {
            for (SocketHandler handler : room) {
                if (handler != sender) {
                    handler.messages.offer("[" + sender.getUsername() + "] >> " + message);
                }
            }
        } else {
            sender.messages.offer("Room " + roomName + " does not exist.");
        }
    }

    synchronized void sendPrivateMessage(String recipientName, String message, SocketHandler sender) {
        SocketHandler recipient = activeHandlersMap.get(recipientName);
        if (recipient != null) {
            String formattedMessage = "[" + sender.getUsername() + "] >> " + message;
            recipient.messages.offer(formattedMessage);
            sender.messages.offer("Sent to [" + recipientName + "] >> " + message);
        } else {
            sender.messages.offer("User " + recipientName + " not found.");
        }
    }
    

    synchronized void joinRoom(String roomName, SocketHandler handler) {
        chatRooms.computeIfAbsent(roomName, k -> ConcurrentHashMap.newKeySet()).add(handler);
    }

    synchronized void leaveRoom(String roomName, SocketHandler handler) {
        Set<SocketHandler> room = chatRooms.get(roomName);
        if (room != null) {
            room.remove(handler);
            if (room.isEmpty()) {
                chatRooms.remove(roomName);
            }
        }
    }

    synchronized boolean add(SocketHandler handler) {
        return activeHandlersMap.putIfAbsent(handler.getUsername(), handler) == null;
    }

    synchronized boolean remove(SocketHandler handler) {
        boolean removed = activeHandlersMap.remove(handler.getUsername(), handler);
        if (removed) {
            for (Set<SocketHandler> room : chatRooms.values()) {
                room.remove(handler);
            }
        }
        return removed;
    }

    synchronized boolean isUsernameAvailable(String username) {
        return !activeHandlersMap.containsKey(username);
    }
}