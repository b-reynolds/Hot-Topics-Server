package io.benreynolds.hottopics.server;

import org.glassfish.tyrus.server.Server;
import javax.websocket.DeploymentException;

class HotTopicsServer {

    private static final String ROOT_PATH = "/hottopics";
    private static final String HOST_NAME = "localhost";
    private static final int PORT = 8025;

    private Server mServer;

    HotTopicsServer() {
        mServer = new Server(HOST_NAME, PORT, ROOT_PATH, HotTopicsEndpoint.class);
    }

    void start() throws DeploymentException {
        mServer.start();

    }

    void stop() {
        mServer.stop();
    }

}