package io.benreynolds.hottopics.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Main {

    public static void main(String[] args) {

        HotTopicsServer hotTopicsServer = new HotTopicsServer();
        try {
            hotTopicsServer.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.print("Press any key to stop the server.");
            reader.readLine();
        } catch (Exception exception) {
            exception.printStackTrace();
        } finally {
            hotTopicsServer.stop();
        }

    }

}
