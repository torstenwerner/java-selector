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
    private Selector selector;

    void start() throws IOException {
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(PORT));
        serverChannel.configureBlocking(false);
        logger.info(() -> "server channel: " + serverChannel);

        selector = Selector.open();
        final SelectionKey selectionKey = serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        selectionKey.attach((Consumer<SelectionKey>) this::handleAccept);
        logger.info(() -> "selector: " + selector);

        while (true) {
            selector.select(this::callback);
        }
    }

    // expects that attachment has been initialized with a Consumer<SelectionKey>
    private void callback(SelectionKey selectionKey) {
        final Consumer<SelectionKey> keyConsumer = (Consumer<SelectionKey>) selectionKey.attachment();
        if (keyConsumer != null) {
            keyConsumer.accept(selectionKey);
        } else {
            logger.severe("attachment missing");
        }
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
