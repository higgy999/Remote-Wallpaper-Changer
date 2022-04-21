package me.toast.wpchanger;

import javafx.application.Platform;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.*;

public class Runnables {

    public static class SendChangeBackground implements Runnable {
        @Override
        public void run() {
            BufferedImage bi;
            try {
                bi = ImageIO.read(new File("./backgrounds/" + Main.backgroundName.getText()));
            } catch (IOException ex) {
                ex.printStackTrace();
                Main.changeBackground.setDisable(false);
                return;
            }
            int[] rawPixels = new int[bi.getWidth() * bi.getHeight() * 4];
            rawPixels = bi.getRGB(0, 0, bi.getWidth(), bi.getHeight(), null, 0, bi.getWidth());

            Packets.ChangeBackgroundStart request = new Packets.ChangeBackgroundStart();
            request.width = bi.getWidth();
            request.height = bi.getHeight();
            request.name = Main.backgroundName.getText();
            request.length = bi.getWidth() * bi.getHeight() * 4;
            request.type = bi.getType();
            Main.SERVER.getConnections()[0].sendTCP(request);

            Platform.runLater(() -> Main.info.setText("Sending background..."));
            for (int i = 0; i < rawPixels.length; i++) {
                Packets.ImageData data = new Packets.ImageData();
                data.index = i;
                data.pixel = rawPixels[i];
                Main.SERVER.getConnections()[0].sendTCP(data);
            }
            Platform.runLater(() -> Main.info.setText("Waiting for response..."));

            Main.SERVER.getConnections()[0].sendTCP(new Packets.ImageDataStop());
            Main.changeBackground.setDisable(false);
        }
    }

    public static class CheckForFiles implements Runnable {
        public static CheckForFiles INSTANCE;
        static {
            try {
                INSTANCE = new CheckForFiles(FileSystems.getDefault().newWatchService());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public boolean keepRunningCheck = true;
        public File fileDirectory = new File("./backgrounds");
        public Path pathDirectory = Paths.get("./backgrounds");
        public WatchService watcher;
        public WatchKey key;

        public CheckForFiles(WatchService watcher) throws IOException {
            this.watcher = watcher;
            pathDirectory.register(this.watcher,
                ENTRY_CREATE,
                ENTRY_DELETE,
                ENTRY_MODIFY);
        }

        @Override
        public void run() {
            while (keepRunningCheck) {
                try {
                    key = watcher.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ClosedWatchServiceException e) {
                    return;
                }
                if (key != null) {
                    for (WatchEvent<?> ignored : key.pollEvents()) {
                        Platform.runLater(this::updateBackgroundAreaText);
                    }
                    boolean valid = key.reset();
                    if (!valid) {
                        break;
                    }
                }
            }
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        public void updateBackgroundAreaText() {
            String[] pathNames = fileDirectory.list();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Objects.requireNonNull(pathNames).length; i ++) {
                sb.append(pathNames[i]).append("\n");
            }
            Main.backgrounds.setText(sb.toString());
        }
    }
}
