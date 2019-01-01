package xyz.its_me;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class SelectHandler {
    private final Logger logger = Logger.getLogger(getClass().getName());

    private final Selector selector = Selector.open();

    public SelectHandler() throws IOException {
    }

    void start() {
        try {
            new ServerHandler(selector).start();
            while (true) {
                selector.select(this::callback);
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to run SelectHandler", e);
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
