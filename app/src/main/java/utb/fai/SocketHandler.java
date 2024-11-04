package utb.fai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;

public class SocketHandler {
    Socket mySocket;
    String clientID;
    String username;
    ActiveHandlers activeHandlers;
    ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<>(20);
    CountDownLatch startSignal = new CountDownLatch(2);
    OutputHandler outputHandler = new OutputHandler();
    InputHandler inputHandler = new InputHandler();
    volatile boolean inputFinished = false;

    public SocketHandler(Socket mySocket, ActiveHandlers activeHandlers) {
        this.mySocket = mySocket;
        clientID = mySocket.getInetAddress().toString() + ":" + mySocket.getPort();
        this.activeHandlers = activeHandlers;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
        activeHandlers.add(this);
    }

    class OutputHandler implements Runnable {
        public void run() {
            OutputStreamWriter writer;
            try {
                startSignal.countDown();
                startSignal.await();
                writer = new OutputStreamWriter(mySocket.getOutputStream(), "UTF-8");
                writer.write("Enter your username: ");
                writer.flush();
                while (!inputFinished) {
                    String m = messages.take();
                    writer.write(m + "\r\n");
                    writer.flush();
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    class InputHandler implements Runnable {
        public void run() {
            try {
                startSignal.countDown();
                startSignal.await();
                BufferedReader reader = new BufferedReader(new InputStreamReader(mySocket.getInputStream(), "UTF-8"));
                String request;
                boolean usernameSet = false;

                while ((request = reader.readLine()) != null) {
                    if (!usernameSet) {
                        String proposedUsername = request.trim();
                        if (activeHandlers.isUsernameAvailable(proposedUsername)) {
                            setUsername(proposedUsername);
                            usernameSet = true;

                            // Add the user to the default "public" room
                            activeHandlers.joinRoom("public", SocketHandler.this);
                            messages.offer("Welcome, " + username + "!");
                            messages.offer("Joined room: public");
                        } else {
                            messages.offer("Username " + proposedUsername + " is already taken. Please choose another one.");
                        }
                        continue;
                    }

                    if (request.startsWith("#sendPrivate ")) {
                        String[] parts = request.split(" ", 3);
                        if (parts.length < 3) {
                            messages.offer("Usage: #sendPrivate <name> <message>");
                        } else {
                            activeHandlers.sendPrivateMessage(parts[1], parts[2], SocketHandler.this);
                        }
                    } else if (request.startsWith("#join ")) {
                        String roomName = request.substring(6).trim();
                        activeHandlers.joinRoom(roomName, SocketHandler.this);
                        messages.offer("Joined room: " + roomName);
                    } else if (request.startsWith("#leave ")) {
                        String roomName = request.substring(7).trim();
                        activeHandlers.leaveRoom(roomName, SocketHandler.this);
                        messages.offer("Left room: " + roomName);
                    } else if (request.equals("#groups")) {
                        String rooms = activeHandlers.chatRooms.entrySet().stream()
                                .filter(entry -> entry.getValue().contains(SocketHandler.this))
                                .map(java.util.Map.Entry::getKey)
                                .collect(java.util.stream.Collectors.joining(", "));
                        messages.offer("You are in rooms: " + rooms);
                    } else if (request.startsWith("#setMyName ")) {
                        String newUsername = request.substring(11).trim();
                        if (activeHandlers.isUsernameAvailable(newUsername)) {
                            String oldUsername = username;
                            activeHandlers.remove(SocketHandler.this); // Remove old username from active handlers
                            setUsername(newUsername);
                            messages.offer("Username changed from " + oldUsername + " to " + newUsername);
                        } else {
                            messages.offer("Username " + newUsername + " is already taken. Please choose another one.");
                        }
                    } else {
                        // Find the current room of the user and send the message to that room
                        Set<String> userRooms = activeHandlers.chatRooms.entrySet().stream()
                                .filter(entry -> entry.getValue().contains(SocketHandler.this))
                                .map(java.util.Map.Entry::getKey)
                                .collect(java.util.stream.Collectors.toSet());
                        for (String room : userRooms) {
                            activeHandlers.sendMessageToRoom(room, SocketHandler.this, request);
                        }
                    }
                }
                inputFinished = true;
                messages.offer("OutputHandler, wakeup and die!");
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            } finally {
                activeHandlers.remove(SocketHandler.this);
            }
        }
    }
}