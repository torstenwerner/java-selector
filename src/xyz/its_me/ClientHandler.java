package xyz.its_me;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

import static java.nio.channels.SelectionKey.*;

class ClientHandler {
    public static final InetSocketAddress REMOTE_ADDRESS = new InetSocketAddress("google.com", 80);

    private final Logger logger = Logger.getLogger(getClass().getName());

    // arbitrarily small for testing
    private static final int CAPACITY = 128;

    private final SocketChannel clientChannel;
    private final SelectionKey clientKey;
    private ByteBuffer clientRequestBuffer;
    private ByteBuffer clientResponseBuffer;
    private final SocketChannel serviceChannel;
    private final SelectionKey serviceKey;
    private ByteBuffer serviceRequestBuffer;
    private ByteBuffer serviceResponseBuffer;

    ClientHandler(SelectHandler selectHandler, SocketChannel clientChannel) throws IOException {
        this.clientChannel = clientChannel;
        clientChannel.configureBlocking(false);
        logger.info(() -> "client channel: " + clientChannel);
        this.clientKey = selectHandler.register(clientChannel, OP_READ, this::handleClient);

        serviceChannel = SocketChannel.open();
        serviceChannel.configureBlocking(false);
        this.serviceKey = selectHandler.register(serviceChannel, OP_CONNECT, this::handleService);
        final boolean status = serviceChannel.connect(REMOTE_ADDRESS);
    }

    private void handleClient(SelectionKey selectionKey) {
        if (!clientChannel.isConnected()) {
            return;
        }
        if (selectionKey.isReadable()) {
            logger.info(() -> "will read from channel: " + clientChannel);
            if (clientRequestBuffer == null) {
                clientRequestBuffer = ByteBuffer.allocate(CAPACITY);
            }
            try {
                clientChannel.read(clientRequestBuffer);
                logger.info(() -> "read bytes " + clientRequestBuffer.position());
                if (serviceRequestBuffer == null) {
                    serviceRequestBuffer = clientRequestBuffer;
                    serviceRequestBuffer.flip();
                    clientRequestBuffer = null;
                } else {
                    clientKey.interestOpsAnd(~OP_READ);
                }
                if (serviceRequestBuffer.position() > 0) {
                    serviceKey.interestOpsOr(OP_WRITE);
                }
            } catch (IOException e) {
                throw new RuntimeException("failed to read", e);
            }
        }
    }

    private void handleService(SelectionKey selectionKey) {
        if (selectionKey.isConnectable()) {
            logger.info(() -> "google connected " + serviceChannel);
            final boolean connected;
            try {
                connected = serviceChannel.finishConnect();
            } catch (IOException e) {
                throw new RuntimeException("failed to finish connection", e);
            }
            logger.info(() -> "google connected " + serviceChannel);
            if (connected) {
                selectionKey.interestOpsAnd(~OP_CONNECT);
            }
            if (serviceRequestBuffer != null) {
                serviceKey.interestOpsOr(OP_WRITE);
            }
        } else if (selectionKey.isWritable()) {
            if (serviceRequestBuffer.remaining() > 0) {
                logger.info(() -> "will write service bytes " + serviceRequestBuffer.remaining());
                try {
                    serviceChannel.write(serviceRequestBuffer);
                    logger.info(() -> "remaining service write bytes " + serviceRequestBuffer.remaining());
                } catch (IOException e) {
                    throw new RuntimeException("failed to write to service", e);
                }
                if (serviceRequestBuffer.remaining() == 0) {
                    serviceRequestBuffer = null;
                    serviceKey.interestOpsAnd(~OP_WRITE);
                    clientKey.interestOpsOr(OP_READ);
                }
            }
        }
    }
}
