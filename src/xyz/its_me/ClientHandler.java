package xyz.its_me;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

import static java.nio.channels.SelectionKey.*;

class ClientHandler {
    public static final InetSocketAddress REMOTE_ADDRESS = new InetSocketAddress("localhost", 8080);

    private final Logger logger = Logger.getLogger(getClass().getName());

    // arbitrarily small for testing
    private static final int CAPACITY = 128;

    private final SelectHandler selectHandler;

    private final SocketChannel clientChannel;
    private final SelectionKey clientKey;
    private ByteBuffer clientRequestBuffer;
    private ByteBuffer clientResponseBuffer;
    private SocketChannel serviceChannel;
    private SelectionKey serviceKey;
    private ByteBuffer serviceRequestBuffer;
    private ByteBuffer serviceResponseBuffer;

    ClientHandler(SelectHandler selectHandler, SocketChannel clientChannel) throws IOException {
        this.selectHandler = selectHandler;
        this.clientChannel = clientChannel;

        clientChannel.configureBlocking(false);
        logger.info(() -> "client channel: " + clientChannel);
        this.clientKey = selectHandler.register(clientChannel, OP_READ, this::handleClient);

        initializeService();
    }

    private void initializeService() throws IOException {
        serviceChannel = SocketChannel.open();
        serviceChannel.configureBlocking(false);
        this.serviceKey = selectHandler.register(serviceChannel, OP_CONNECT, this::handleService);
        serviceChannel.connect(REMOTE_ADDRESS);
    }

    private void handleClient() {
        if (clientChannel.isConnected()) {
            moveRequestBuffers();
            if (clientKey.isReadable()) {
                readFromClient(); // may close the channel
            }
        }
        if (clientChannel.isConnected() && clientKey.isWritable()) {
            writeToClient();
        }
    }

    private void readFromClient() {
        logger.info(() -> "will read from channel: " + clientChannel);
        if (clientRequestBuffer == null) {
            clientRequestBuffer = ByteBuffer.allocate(CAPACITY);
        }
        if (!clientRequestBuffer.hasRemaining()) {
            clientKey.interestOpsAnd(~OP_READ);
            return;
        }
        try {
            final int count = clientChannel.read(clientRequestBuffer);
            if (count < 0) {
                logger.warning("error reading from client, will shutdown connection");
                clientKey.interestOps(0);
                serviceKey.interestOps(0);
                clientChannel.close();
                serviceChannel.close();
                return;
            }
            logger.info(() -> "read bytes " + count);
        } catch (IOException e) {
            throw new RuntimeException("failed to read from client", e);
        }
        moveRequestBuffers();
        if (serviceRequestBuffer != null && serviceRequestBuffer.hasRemaining()) {
            serviceKey.interestOpsOr(OP_WRITE);
        }
    }

    private void writeToClient() {
        if (clientResponseBuffer == null) {
            clientKey.interestOpsAnd(~OP_WRITE);
            return;
        }
        if (clientResponseBuffer.hasRemaining()) {
            logger.info(() -> "will write service bytes " + clientResponseBuffer.remaining());
            try {
                clientChannel.write(clientResponseBuffer);
                logger.info(() -> "remaining client write bytes " + clientResponseBuffer.remaining());
            } catch (IOException e) {
                throw new RuntimeException("failed to write to client", e);
            }
        }
        if (!clientResponseBuffer.hasRemaining()) {
            clientResponseBuffer = null;
            clientKey.interestOpsAnd(~OP_WRITE);
            serviceKey.interestOpsOr(OP_READ);
        }
        moveResponseBuffers();
    }

    private void handleService() {
        if (serviceKey.isConnectable()) {
            finishConnectToService();
        }
        if (serviceChannel.isConnected()) {
            if (serviceKey.isWritable()) {
                writeToService(); // may close the channel
            }
            moveRequestBuffers();
        }
        if (serviceChannel.isConnected() && serviceKey.isReadable()) {
            readFromService();
        }
    }

    private void finishConnectToService() {
        logger.info(() -> "google connected " + serviceChannel);
        final boolean connected;
        try {
            connected = serviceChannel.finishConnect();
        } catch (IOException e) {
            throw new RuntimeException("failed to finish connection", e);
        }
        logger.info(() -> "google connected " + serviceChannel);
        if (connected) {
            serviceKey.interestOpsAnd(~OP_CONNECT);
            serviceKey.interestOpsOr(OP_READ);
        }
    }

    // serviceChannel must be connected

    private void writeToService() {
        if (serviceRequestBuffer == null) {
            serviceKey.interestOpsAnd(~OP_WRITE);
            return;
        }
        if (serviceRequestBuffer.hasRemaining()) {
            logger.info(() -> "will write service bytes " + serviceRequestBuffer.remaining());
            try {
                serviceChannel.write(serviceRequestBuffer);
                logger.info(() -> "remaining service write bytes " + serviceRequestBuffer.remaining());
            } catch (IOException e) {
                throw new RuntimeException("failed to write to service", e);
            }
        }
        if (!serviceRequestBuffer.hasRemaining()) {
            serviceRequestBuffer = null;
            serviceKey.interestOpsAnd(~OP_WRITE);
            clientKey.interestOpsOr(OP_READ);
        }
        moveRequestBuffers();
    }

    private void readFromService() {
        logger.info(() -> "will read from channel: " + serviceChannel);
        if (serviceResponseBuffer == null) {
            serviceResponseBuffer = ByteBuffer.allocate(CAPACITY);
        }
        if (!serviceResponseBuffer.hasRemaining()) {
            serviceKey.interestOpsAnd(~OP_READ);
            return;
        }
        try {
            final int count = serviceChannel.read(serviceResponseBuffer);
            if (count < 0) {
                logger.warning("error reading from service, will reconnect");
                serviceKey.interestOps(0);
                serviceChannel.close();
                initializeService();
                return;
            }
            logger.info(() -> "read bytes " + count);
        } catch (IOException e) {
            throw new RuntimeException("failed to read from service", e);
        }
        if (serviceResponseBuffer.position() == 0) {
            serviceKey.interestOpsAnd(~OP_READ);
            return;
        }
        moveResponseBuffers();
        if (clientResponseBuffer != null && clientResponseBuffer.hasRemaining()) {
            clientKey.interestOpsOr(OP_WRITE);
        }
    }

    private void moveRequestBuffers() {
        if (serviceRequestBuffer == null && clientRequestBuffer != null) {
            serviceRequestBuffer = clientRequestBuffer;
            clientRequestBuffer = null;
            serviceRequestBuffer.flip();
            clientKey.interestOpsOr(OP_READ);
            serviceKey.interestOpsOr(OP_WRITE);
        }
    }

    private void moveResponseBuffers() {
        if (clientResponseBuffer == null && serviceResponseBuffer != null) {
            clientResponseBuffer = serviceResponseBuffer;
            serviceResponseBuffer = null;
            clientResponseBuffer.flip();
            serviceKey.interestOpsOr(OP_READ);
            clientKey.interestOpsOr(OP_WRITE);
        }
    }
}
