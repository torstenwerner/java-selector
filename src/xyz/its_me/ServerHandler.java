package xyz.its_me;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

import static java.nio.channels.SelectionKey.OP_ACCEPT;

public class ServerHandler {
    private final Logger logger = Logger.getLogger(getClass().getName());

    private static final int PORT = 7777;

    private final ServerSocketChannel serverChannel;
    private final SelectHandler selectHandler;
    private final SelectionKey selectionKey;

    public ServerHandler(SelectHandler selectHandler) throws IOException {
        this.selectHandler = selectHandler;
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(PORT));
        serverChannel.configureBlocking(false);
        logger.info(() -> "server channel: " + serverChannel);

        selectionKey = selectHandler.register(serverChannel, OP_ACCEPT, this::handleAccept);
    }

    private void handleAccept() {
        try {
            if (selectionKey.isAcceptable()) {
                final SocketChannel clientChannel = serverChannel.accept();
                new ClientHandler(selectHandler, clientChannel);
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to accept", e);
        }
    }
}
