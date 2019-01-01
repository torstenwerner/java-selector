package xyz.its_me;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.logging.Logger;

public class SelectHandler {
    private final Logger logger = Logger.getLogger(getClass().getName());

    private final Selector selector = Selector.open();

    public SelectHandler() throws IOException {
    }

    void start() {
        try {
            new ServerHandler(this);
            while (true) {
                selector.select(this::callback);
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to run SelectHandler", e);
        }
    }

    SelectionKey register(AbstractSelectableChannel channel, int ops, Runnable callback) {
        try {
            return channel.register(selector, ops, callback);
        } catch (ClosedChannelException e) {
            throw new RuntimeException("failed to register channel with selector");
        }
    }

    // expects that attachment has been initialized with a Runnable
    private void callback(SelectionKey selectionKey) {
        final Runnable keyConsumer = (Runnable) selectionKey.attachment();
        if (keyConsumer != null) {
            keyConsumer.run();
        } else {
            logger.severe("attachment missing");
        }
    }
}
