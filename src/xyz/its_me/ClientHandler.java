package xyz.its_me;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static java.nio.channels.SelectionKey.OP_CONNECT;
import static java.nio.channels.SelectionKey.OP_READ;

class ClientHandler {
    public static final InetSocketAddress REMOTE_ADDRESS = new InetSocketAddress("google.com", 80);

    private final Logger logger = Logger.getLogger(getClass().getName());

    // arbitrarily small for testing
    private static final int CAPACITY = 128;

    private final Selector selector;
    private final SocketChannel clientChannel;
    private final SocketChannel serviceChannel;
    private final ByteBuffer requestBuffer = ByteBuffer.allocateDirect(CAPACITY);
    private final ByteBuffer responseBuffer = ByteBuffer.allocateDirect(CAPACITY);

    ClientHandler(Selector selector, SocketChannel clientChannel) throws IOException {
        this.selector = selector;

        this.clientChannel = clientChannel;
        clientChannel.configureBlocking(false);
        logger.info(() -> "client channel: " + clientChannel);
        final SelectionKey clientKey = clientChannel.register(selector, OP_READ);
        clientKey.attach((Consumer<SelectionKey>) this::handleClient);

        serviceChannel = SocketChannel.open();
        serviceChannel.configureBlocking(false);
        final SelectionKey selectionKey = serviceChannel.register(selector, OP_CONNECT);
        selectionKey.attach((Consumer<SelectionKey>) this::handleService);
        final boolean status = serviceChannel.connect(REMOTE_ADDRESS);
    }

    private void handleClient(SelectionKey selectionKey) {
        if (!clientChannel.isConnected()) {
            return;
        }
        if (selectionKey.isReadable()) {
            logger.info(() -> "will read from channel: " + clientChannel);
            try {
                clientChannel.read(requestBuffer);
            } catch (IOException e) {
                throw new RuntimeException("failed to read", e);
            }
            logger.info(() -> "read remaining: " + requestBuffer.remaining());
            requestBuffer.flip();
            final String data = StandardCharsets.UTF_8.decode(requestBuffer).toString();
            logger.info(() -> "data: " + data);
            requestBuffer.clear();
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
        }
    }
}
