package xyz.its_me;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class ServerHandler {
    private final Logger logger = Logger.getLogger(getClass().getName());

    private static final int PORT = 7777;

    private ServerSocketChannel serverChannel;
    private final Selector selector;

    public ServerHandler(Selector selector) {
        this.selector = selector;
    }

    void start() throws IOException {
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(PORT));
        serverChannel.configureBlocking(false);
        logger.info(() -> "server channel: " + serverChannel);

        final SelectionKey selectionKey = serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        selectionKey.attach((Consumer<SelectionKey>) this::handleAccept);
    }

    private void handleAccept(SelectionKey selectionKey) {
        try {
            if (selectionKey.isAcceptable()) {
                final SocketChannel clientChannel = serverChannel.accept();
                new ClientHandler(selector, clientChannel);
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to accept", e);
        }
    }
}
