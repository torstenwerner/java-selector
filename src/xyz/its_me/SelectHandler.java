package xyz.its_me;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class SelectHandler {
    private final Logger logger = Logger.getLogger(getClass().getName());

    private final Selector selector = Selector.open();

    public SelectHandler() throws IOException {
    }

    void start() {
        try {
            new ServerHandler(this).start();
            while (true) {
                selector.select(this::callback);
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to run SelectHandler", e);
        }
    }

    SelectionKey register(AbstractSelectableChannel channel, int ops, Consumer<SelectionKey> callback) {
        try {
            return channel.register(selector, ops, callback);
        } catch (ClosedChannelException e) {
            throw new RuntimeException("failed to register channel with selector");
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
}
