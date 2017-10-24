package com.bringholm.compactminecraftclient.v1_12_2;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A compact Minecraft single-class Minecraft client capable of extension.
 * The default functionality is just to do the bare minimum to not get kicked
 * by the server (IE proper login sequence, respond to keep alive packets and
 * disconnect packets). This functionality can of course be extended to include
 * handling all packets.
 *
 * When initialized, this class creates a new Thread (and a separate input Thread)
 * to run its operations on, so the caller thread can carry on with other operations.
 *
 * @author AlvinB
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class CompactMinecraftClient {
    /**
     * Registered packets in State PLAY
     */
    protected static Map<Integer, Constructor<? extends PacketInbound>> playPackets = new HashMap<>();
    /**
     * Registered packets in State LOGIN
     */
    protected static Map<Integer, Constructor<? extends PacketInbound>> loginPackets = new HashMap<>();
    /**
     * Registered packets in State STATUS
     */
    protected static Map<Integer, Constructor<? extends PacketInbound>> statusPackets = new HashMap<>();

    static {
        registerInboundPacket(LoginInPacketDisconnect.class, LoginInPacketDisconnect.ID, State.LOGIN);
        registerInboundPacket(LoginInPacketEncryptionRequest.class, LoginInPacketEncryptionRequest.ID, State.LOGIN);
        registerInboundPacket(LoginInPacketLoginSuccess.class, LoginInPacketLoginSuccess.ID, State.LOGIN);
        registerInboundPacket(LoginInPacketSetCompression.class, LoginInPacketSetCompression.ID, State.LOGIN);

        registerInboundPacket(PlayInPacketKeepAlive.class, PlayInPacketKeepAlive.ID, State.PLAY);
        registerInboundPacket(PlayInPacketDisconnect.class, PlayInPacketDisconnect.ID, State.PLAY);
    }

    /**
     * Registers a Packet class to assign to a Packet ID.
     *
     * This Packet can also be used to overload existing packet classes
     * and replace them with your own.
     *
     * Any packets you do register must have a constructor taking DataInputStream where
     * the deserialization is done. Note that you only need to read the data of the packet
     * in this constructor, the packet ID is read elsewhere and excluded from the stream.
     *
     * @param clazz the class.
     * @param id the id
     * @param state the state in which the packet is sent
     */
    public synchronized static void registerInboundPacket(Class<? extends PacketInbound> clazz, int id, State state) {
        Constructor<? extends PacketInbound> constructor;
        try {
            //noinspection JavaReflectionMemberAccess
            constructor = clazz.getConstructor(DataInputStream.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class " + clazz + " does not have a constructor taking DataInputStream!");
        }
        switch (state) {
            case PLAY:
                playPackets.put(id, constructor);
                break;
            case LOGIN:
                loginPackets.put(id, constructor);
                break;
            case STATUS:
                statusPackets.put(id, constructor);
                break;
        }
    }

    /**
     * The current state of protocol the connection is in
     */
    protected State state;
    /**
     * The connection socket
     */
    protected Socket socket;
    /**
     * The InputStream from the socket. Should only be read from on the input Thread.
     */
    protected volatile DataInputStream inputStream;
    /**
     * The OutputStream from the socket. Should only be written to on the main Thread.
     */
    protected DataOutputStream outputStream;
    /**
     * The current compression threshold for the connection. Is set by the Set Compression
     * packet.
     */
    protected int compressionThreshold = -1;
    /**
     * The hostname that was used to connect.
     */
    protected String host;
    /**
     * The port that was used to connect.
     */
    protected int port;
    /**
     * The username used to connect. Sent to the server in the Login Start
     * packet, but only used if the server is in offline mode.
     */
    protected String username;

    /**
     * The thread on which to do all of the reading from the socket InputStream.
     */
    protected volatile Thread inputThread;
    /**
     * The main thread.
     */
    protected volatile Thread mainThread;
    /**
     * Whether or not the client is running.
     */
    protected volatile boolean running = true;
    /**
     * The task queue for tasks waiting to be executed on the main thread. Should
     * only be read from the main Thread.
     */
    protected volatile Queue<MainThreadTask> taskQueue = new ConcurrentLinkedQueue<>();

    /**
     * Creates a new Thread for the client to run on and initializes all the necessary
     * data.
     *
     * @param username The username that will be used to connect. Can be null if
     *                 all the connection intends to do is query status.
     * @param host the host to connect to
     * @param port the port to connect to
     */
    public CompactMinecraftClient(String username, String host, int port) {
        mainThread = new Thread(() -> {
            try {
                this.host = host;
                this.port = port;
                this.socket = new Socket(host, port);
                this.inputStream = new DataInputStream(socket.getInputStream());
                this.outputStream = new DataOutputStream(socket.getOutputStream());
                this.inputThread = new Thread(() -> {
                    try {
                        while (running) {
                            readPacket();
                        }
                    } catch (IOException | DataFormatException e) {
                        System.err.println("Encountered error while reading packets!");
                        e.printStackTrace();
                        try {
                            close();
                        } catch (IOException e1) {
                            System.err.println("Failed to close client on error!");
                            e1.printStackTrace();
                        }
                    }
                });
                this.inputThread.start();
                this.username = username;
                while (running) {
                    processTasks();
                }
            } catch (Exception e) {
                System.err.println("An error occurred while initializing client!");
                e.printStackTrace();
                try {
                    close();
                } catch (IOException e1) {
                    System.err.println("Failed to close client on error!");
                    e1.printStackTrace();
                }
            }
        });
        mainThread.start();
    }

    /**
     * Processes all the main thread tasks waiting to be executed. Should
     * only be called from the main thread.
     *
     * @throws IOException if the task caused an IOException
     */
    protected void processTasks() throws Exception {
        while (!taskQueue.isEmpty()) {
            taskQueue.poll().runTask();
        }
    }

    /**
     * Sends the necessary packets to start the login
     * sequence.
     *
     * If this method is not called by the main thread,
     * a task will be scheduled to run it on the main thread.
     *
     * @throws IOException if an IO error occurred
     */
    public void login() throws IOException {
        if (!isMainThread()) {
            taskQueue.add(this::login);
            return;
        }
        setState(State.LOGIN);
        sendPacket(new LoginOutPacketLoginStart(this.username));
    }

    protected void enableEncryption(Key key) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IOException {
        if (!isMainThread()) {
            taskQueue.add(() -> this.enableEncryption(key));
            return;
        }
        Cipher encryptCipher = Cipher.getInstance("AES/CFB8/NoPadding");
        encryptCipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(key.getEncoded()));
        this.outputStream = new DataOutputStream(new CipherOutputStream(socket.getOutputStream(), encryptCipher));
        Cipher decryptCipher = Cipher.getInstance("AES/CFB8/NoPadding");
        decryptCipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(key.getEncoded()));
        this.inputStream = new DataInputStream(new CipherInputStream(socket.getInputStream(), decryptCipher));
    }

    /**
     * Sends the necessary packets to change the connection state.
     *
     * @param state the state to put the connection into
     * @throws IOException if an IO error occurred
     */
    protected void setState(State state) throws IOException {
        sendPacket(new HandshakeOutPacketHandshake(this.getProtocolVersion(), this.host, (short) this.port, state));
        this.state = state;
    }

    /**
     * Called when the client is disconnected from the server without errors.
     *
     * @param reason the disconnect reason
     */
    public void handleDisconnect(String reason) throws IOException {
        System.out.println("Disconnected from server: " + reason);
        // This is already done by the close method, but since that
        // is executed on the main thread, there is a chance the
        // input thread might continue reading packets and
        // throw exceptions
        this.running = false;
        this.close();
    }

    /**
     * Checks if the current thread is the Main Thread.
     *
     * @return whether or not the current thread is the Main Thread.
     */
    protected boolean isMainThread() {
        return Thread.currentThread() == this.mainThread;
    }

    /**
     * Gets the current protocol version.
     *
     * @return the protocol version
     */
    protected int getProtocolVersion() {
        return 340;
    }

    /**
     * Gets the current state the client is in.
     *
     * @return the state
     */
    public State getState() {
        return state;
    }

    /**
     * Closes the client.
     *
     * If this method is not called by the main thread,
     * a task will be scheduled to run it on the main thread.
     *
     * @throws IOException if an IO error occurred
     */
    public void close() throws IOException {
        if (!isMainThread()) {
            taskQueue.add(this::close);
            return;
        }
        running = false;
        if (inputStream != null) {
            inputStream.close();
        }
        if (outputStream != null) {
            outputStream.close();
        }
        if (socket != null) {
            socket.close();
        }
    }

    /**
     * Sends an outgoing packet through the socket.
     *
     * If this method is not called by the main thread,
     * a task will be scheduled to run it on the main thread.
     *
     * @param packet the packet
     * @throws IOException if an IO error occurred
     */
    public void sendPacket(PacketOutbound packet) throws IOException {
        if (!isMainThread()) {
            taskQueue.add(() -> sendPacket(packet));
            return;
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
            writeVarInt(dataOutputStream, packet.getId());
            packet.write(dataOutputStream);
            byte[] data = outputStream.toByteArray();
            byte[] finalPacketData;
            if (compressionThreshold > 0) {
                finalPacketData = makeCompressedPacket(data);
            } else {
                finalPacketData = data;
            }
            writeVarInt(this.outputStream, finalPacketData.length);
            this.outputStream.write(finalPacketData);
        }
    }

    /**
     * Makes a compressed packet.
     *
     * @param uncompressedData the uncompressed packet data, including the packet ID
     * @return the compressed data, prefixed with the uncompressed length, or zero if
     *         the packet is not compressed.
     * @throws IOException if an IO error occurred
     */
    protected byte[] makeCompressedPacket(byte[] uncompressedData) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
            byte[] compressedData = (uncompressedData.length >= compressionThreshold ? zlibCompress(uncompressedData) : uncompressedData);
            // The length should be sent as zero if the data is uncompressed
            int uncompressedLength = (uncompressedData.length >= compressionThreshold ? uncompressedData.length : 0);
            writeVarInt(dataOutputStream, uncompressedLength);
            outputStream.write(compressedData);
            return outputStream.toByteArray();
        }
    }

    /**
     * Compresses a series of bytes using zlib compression.
     *
     * @param data the uncompressed data
     * @return the compressed data
     * @throws IOException if an IO error occurred.
     */
    protected byte[] zlibCompress(byte[] data) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Deflater deflater = new Deflater();
            try {
                deflater.setInput(data);
                byte[] buffer = new byte[1024];
                while (!deflater.finished()) {
                    int count = deflater.deflate(buffer);
                    outputStream.write(buffer, 0, count);
                }
            } finally {
                deflater.end();
            }
            return outputStream.toByteArray();
        }
    }

    /**
     * Reads a packet from the InputStream
     *
     * @throws IOException if an IO error occurred
     * @throws DataFormatException if the compressed data was malformed
     */
    public void readPacket() throws IOException, DataFormatException {
        if (compressionThreshold > 0) {
            readCompressedPacket();
        } else {
            readUncompressedPacket();
        }
    }

    /**
     * Read an uncompressed packet from the InputStream
     *
     * @throws IOException if an IO error occurred
     */
    protected void readUncompressedPacket() throws IOException {
        int totalLength = readVarInt(this.inputStream);
        // [0] = packetID [1] = byte length of packet ID
        int[] packetID = readVarIntWithLength(this.inputStream);
        byte[] data = new byte[totalLength - packetID[1]];
        this.inputStream.readFully(data);
        this.delegatePacket(packetID[0], data);
    }

    /**
     * Reads a compressed packet from the InputStream
     *
     * @throws IOException if an IO error occurred
     * @throws DataFormatException if the compressed data was malformed
     */
    protected void readCompressedPacket() throws IOException, DataFormatException {
        int totalLength = readVarInt(this.inputStream);
        // index 0 is the uncompressed length of the data + packet id, index 1 is the number of bytes the it took up when serialized
        int[] uncompressedDataLength = readVarIntWithLength(this.inputStream);
        if (uncompressedDataLength[0] == 0) {
            // This packet is uncompressed!
            // Length of packet ID + data
            int packetIDAndDataLength = totalLength - uncompressedDataLength[1];
            // [0] = packetID [1] = byte length of packetID var int
            int[] packetID = readVarIntWithLength(this.inputStream);
            byte[] packetData = new byte[packetIDAndDataLength - packetID[1]];
            this.inputStream.readFully(packetData);
            this.delegatePacket(packetID[0], packetData);
        } else {
            byte[] compressedData = new byte[totalLength - uncompressedDataLength[1]];
            this.inputStream.readFully(compressedData);
            try (DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(zlibUncompress(compressedData, uncompressedDataLength[0])))) {
                int[] packetID = readVarIntWithLength(inputStream);
                byte[] packetData = new byte[uncompressedDataLength[0] - packetID[1]];
                inputStream.readFully(packetData);
                this.delegatePacket(packetID[0], packetData);
            }
        }
    }

    /**
     * Uncompresses a series of bytes using zlib compression.
     *
     * @param data the compressed data
     * @param uncompressedLength the uncompressed length of the data
     * @return the uncompressed data
     * @throws DataFormatException if the compressed data was malformed
     */
    protected byte[] zlibUncompress(byte[] data, int uncompressedLength) throws DataFormatException {
        byte[] uncompressed = new byte[uncompressedLength];
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data);
            inflater.inflate(uncompressed);
        } finally {
            inflater.end();
        }
        return uncompressed;
    }

    /**
     * Delegates a packet.
     *
     * @param id the packet ID
     * @param data the data of the packet
     * @throws IOException if an IO error occurred
     */
    protected void delegatePacket(int id, byte[] data) throws IOException {
        try (DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(data))) {
            switch (state) {
                case PLAY:
                    if (playPackets.containsKey(id)) {
                        PacketInbound packet = playPackets.get(id).newInstance(inputStream);
                        packet.handle(this);
                    }
                    break;
                case LOGIN:
                    if (loginPackets.containsKey(id)) {
                        PacketInbound packet = loginPackets.get(id).newInstance(inputStream);
                        packet.handle(this);
                    }
                    break;
                case STATUS:
                    if (statusPackets.containsKey(id)) {
                        PacketInbound packet = statusPackets.get(id).newInstance(inputStream);
                        packet.handle(this);
                    }
                    break;
            }
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            System.err.println("Failed to instantiate packet " + id + "!");
            e.printStackTrace();
        }
    }

    /**
     * Inbound packet sent when the client is disconnected from the server.
     */
    public static class PlayInPacketDisconnect extends PacketInbound {
        public static final int ID = 0x1A;

        private String reason;

        public PlayInPacketDisconnect(DataInputStream inputStream) throws IOException {
            this.reason = readString(inputStream);
        }

        @Override
        public void handle(CompactMinecraftClient client) throws IOException {
            client.handleDisconnect(this.reason);
        }
    }

    /**
     * Outbound packet sent to respond to a Keep Alive packet from the server.
     */
    public static class PlayOutPacketKeepAlive extends PacketOutbound {
        public static final int ID = 0x0B;

        private int id;

        public PlayOutPacketKeepAlive(int id) {
            super(ID);
            this.id = id;
        }

        @Override
        public void write(DataOutputStream outputStream) throws IOException {
            writeVarInt(outputStream, this.id);
        }
    }

    /**
     * Inbound packet sent to make sure the connection is still alive.
     */
    public static class PlayInPacketKeepAlive extends PacketInbound {
        public static final int ID = 0x1F;

        private int id;

        public PlayInPacketKeepAlive(DataInputStream inputStream) throws IOException {
            this.id = readVarInt(inputStream);
        }

        @Override
        public void handle(CompactMinecraftClient client) throws IOException {
            client.sendPacket(new PlayOutPacketKeepAlive(id));
        }
    }

    /**
     * Inbound packet sent to set the compression threshold
     */
    public static class LoginInPacketSetCompression extends PacketInbound {
        public static final int ID = 0x03;

        private int threshold;

        public LoginInPacketSetCompression(DataInputStream inputStream) throws IOException {
            this.threshold = readVarInt(inputStream);
        }

        @Override
        public void handle(CompactMinecraftClient client) throws IOException {
            client.compressionThreshold = this.threshold;
        }
    }

    /**
     * Inbound packet sent when the login sequence is completed.
     */
    public static class LoginInPacketLoginSuccess extends PacketInbound {
        public static final int ID = 0x02;

        private String uuid;
        private String username;

        public LoginInPacketLoginSuccess(DataInputStream inputStream) throws IOException {
            this.uuid = readString(inputStream);
            this.username = readString(inputStream);
        }

        @Override
        public void handle(CompactMinecraftClient client) throws IOException {
            System.out.println("Successfully connected to " + client.host + ":" + client.port + "!");
            System.out.println("Username: " + username + " UUID: " + uuid);
            client.state = State.PLAY;
        }
    }

    /**
     * Inbound packet sent to enable encryption.
     */
    public static class LoginInPacketEncryptionRequest extends PacketInbound {
        public static final int ID = 0x01;

        private String serverID;
        private byte[] publicKey;
        private byte[] verifyToken;

        public LoginInPacketEncryptionRequest(DataInputStream inputStream) throws IOException {
            this.serverID = readString(inputStream);
            this.publicKey = new byte[readVarInt(inputStream)];
            inputStream.readFully(this.publicKey);
            this.verifyToken = new byte[readVarInt(inputStream)];
            inputStream.readFully(this.verifyToken);
        }

        @Override
        public void handle(CompactMinecraftClient client) throws IOException {
            client.handleDisconnect("Protocol Encryption is not supported!");
        }
    }

    /**
     * Inbound packet sent when the client is disconnected during login.
     */
    public static class LoginInPacketDisconnect extends PacketInbound {
        public static final int ID = 0x00;

        private String reason;

        public LoginInPacketDisconnect(DataInputStream inputStream) throws IOException {
            this.reason = readString(inputStream);
        }

        @Override
        public void handle(CompactMinecraftClient client) throws IOException {
            client.handleDisconnect(this.reason);
        }
    }

    /**
     * Outbound packet sent to start the login sequence.
     */
    public static class LoginOutPacketLoginStart extends PacketOutbound {
        public static final int ID = 0x00;

        private String username;

        public LoginOutPacketLoginStart(String username) {
            super(ID);
            this.username = username;
        }

        @Override
        public void write(DataOutputStream outputStream) throws IOException {
            writeString(outputStream, username);
        }
    }

    /**
     * Outbound packet sent to switch to a new connection state.
     */
    public static class HandshakeOutPacketHandshake extends PacketOutbound {
        public static final int ID = 0x00;

        private int protocolVersion;
        private String address;
        private short port;
        private State nextState;

        public HandshakeOutPacketHandshake(int protocolVersion, String address, short port, State nextState) {
            super(ID);
            this.protocolVersion = protocolVersion;
            this.address = address;
            this.port = port;
            this.nextState = nextState;
        }

        @Override
        public void write(DataOutputStream outputStream) throws IOException {
            writeVarInt(outputStream, this.protocolVersion);
            writeString(outputStream, this.address);
            outputStream.writeShort(port);
            writeVarInt(outputStream, nextState.getId());
        }
    }

    /**
     * Abstract class for outbound packets
     */
    public static abstract class PacketOutbound {
        /**
         * The id of this packet
         */
        protected int id;

        /**
         * Initializes this outbound packet
         *
         * @param id the packet ID
         */
        public PacketOutbound(int id) {
            this.id = id;
        }

        /**
         * Gets the id of this packet.
         *
         * @return the packet ID
         */
        public int getId() {
            return id;
        }

        /**
         * Writes this packet to the stream.
         *
         * @param outputStream the output stream
         * @throws IOException if an IO error occurred
         */
        public abstract void write(DataOutputStream outputStream) throws IOException;
    }

    /**
     * Abstract class for inbound packets.
     *
     * All subclasses must be registered with #registerInboundPacket(Class, int),
     * and have a constructor taking DataInputStream.
     */
    public static abstract class PacketInbound {
        public abstract void handle(CompactMinecraftClient client) throws IOException;
    }

    /**
     * Writes a VarInt to the specified stream.
     *
     * @param outputStream the OutputStream
     * @param integer the int to write
     * @throws IOException if an IO error occurred
     */
    public static void writeVarInt(DataOutputStream outputStream, int integer) throws IOException {
        while (true) {
            if ((integer & 0xFFFFFF80) == 0) {
                outputStream.writeByte((byte) integer);
                break;
            }

            outputStream.writeByte((byte) (integer & 0x7F | 0x80));
            integer >>>= 7;
        }
    }

    /**
     * Writes a String to the specified stream.
     *
     * @param outputStream the OutputStream
     * @param string the String to write
     * @throws IOException if an IO error occurred
     */
    public static void writeString(DataOutputStream outputStream, String string) throws IOException {
        byte[] data = string.getBytes("UTF-8");
        writeVarInt(outputStream, data.length);
        outputStream.write(data);
    }

    /**
     * Reads a String from the specified stream.
     *
     * @param inputStream the InputStream
     * @return the String
     * @throws IOException if an IO error occurred
     */
    public static String readString(DataInputStream inputStream) throws IOException {
        int length = readVarInt(inputStream);
        byte[] data = new byte[length];
        inputStream.readFully(data);
        return new String(data, "UTF-8");
    }

    /**
     * Reads a VarInt from the specified stream.
     *
     * @param inputStream the InputStream
     * @return the int
     * @throws IOException if an IO error occurred
     */
    public static int readVarInt(DataInputStream inputStream) throws IOException {
        return readVarIntWithLength(inputStream)[0];
    }

    /**
     * Reads a VarInt and its length from the specified stream.
     *
     * @param inputStream the InputStream
     * @return an array with the int at index 0
     *         and the length at index 1
     * @throws IOException if an IO error occurred
     */
    public static int[] readVarIntWithLength(DataInputStream inputStream) throws IOException {
        int readInt = 0;
        int length = 0;
        while (true) {
            byte currentByte = inputStream.readByte();
            readInt |= (currentByte & 0x7F) << length++ * 7;
            if (length > 5) {
                throw new RuntimeException("Incoming VarInt was too big!");
            }
            if ((currentByte & 0x80) != 128) {
                break;
            }
        }
        return new int[] {readInt, length};
    }

    /**
     * Interface for tasks to be executed on the Main Thread.
     */
    @FunctionalInterface
    public interface MainThreadTask {
        /**
         * Runs the task.
         *
         * @throws Exception if an error occurred
         */
        void runTask() throws Exception;
    }

    /**
     * An enum for all the connection states.
     */
    public enum State {
        PLAY(0),
        STATUS(1),
        LOGIN(2);

        private int id;

        State(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public static State fromId(int id) {
            switch (id) {
                case 0:
                    return PLAY;
                case 1:
                    return STATUS;
                case 2:
                    return LOGIN;
                default:
                    throw new IllegalArgumentException("Unknown state id: " + id);
            }
        }
    }
}
