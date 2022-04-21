package me.toast.wpchanger;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.win32.W32APIOptions;
import org.apache.commons.io.FileUtils;

public class Main {

    public static Client CLIENT;
    public static String SERVER_IP;

    public boolean stopCleaning = false;

    public void run(String[] args) {
        if (args[0] == null) {
            System.err.println("No server IP set for first argument");
            return;
        }
        SERVER_IP = args[0];
        Thread.currentThread().setName("Main");

        CLIENT = new Client(1000000 * 128, 1000000 * 128);
        RegisterPackets(CLIENT.getKryo());
        Thread thread = new Thread(CLIENT, "Networking"); thread.start();

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            if (!stopCleaning) {
                try {
                    FileUtils.cleanDirectory(new File("./received"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 1, 10, TimeUnit.MINUTES);

        tryConnect();

        CLIENT.addListener(LISTENER);
    }

    public void tryConnect() {
        while (!CLIENT.isConnected()) {
            try {
                CLIENT.connect(5000, SERVER_IP, 54556);
                if (CLIENT.isConnected())
                    return;
                new Thread(() -> {
                    try { Thread.sleep(60000); } catch (InterruptedException ex) { ex.printStackTrace(); }
                    tryConnect();
                }).start();
            } catch (IOException e) {
                System.err.println("Couldn't connect retrying in 1 minute...");
            }
        }
    }

    public Listener LISTENER = new Listener() {
        int[] rawPixels;
        ChangeBackgroundStart startingPacket;

        public void disconnected(Connection connection) {
            System.out.println("Disconnected from Server!");
            new Thread(() -> {
                try { Thread.sleep(60000); } catch (InterruptedException ex) { ex.printStackTrace(); }
                tryConnect();
            }).start();
        }
        public void received(Connection connection, Object object) {
            if (object instanceof ChangeBackgroundStart packet) {
                System.out.println("Got ChangeBackgroundStart!");
                startingPacket = packet;
                rawPixels = new int[packet.length];
                stopCleaning = true;
            }

            if (object instanceof ImageData packet) {
                System.out.println("Got ImageData!");
                rawPixels[packet.index] = packet.pixel;
            }

            if (object instanceof ImageDataStop) {
                ImageSendResult result = new ImageSendResult();
                System.out.println("Got ImageDataStop!");
                BufferedImage bi = new BufferedImage(startingPacket.width, startingPacket.height, startingPacket.type);
                bi.setRGB(0, 0, startingPacket.width, startingPacket.height, rawPixels, 0, startingPacket.width);
                try {
                    File file = new File("./received/" + startingPacket.name);
                    file.mkdirs();
                    ImageIO.write(bi, startingPacket.name.substring(startingPacket.name.lastIndexOf('.') + 1), file);
                } catch (IOException e) {
                    result.success = false;
                    CLIENT.sendTCP(result);
                    e.printStackTrace();
                }
                ChangeWallpaper(new File("./received/" + startingPacket.name).getAbsolutePath());

                result.success = true;
                CLIENT.sendTCP(result);

                rawPixels = null;
                startingPacket = null;
                stopCleaning = false;
            }
        }
    };

    public static class ChangeBackgroundStart { String name; int width, height, length, type;}
    public static class ImageData { int index, pixel; }
    public static class ImageDataStop {}
    public static class ImageSendResult { boolean success; }

    public void RegisterPackets(Kryo kryo) {
        kryo.register(ChangeBackgroundStart.class);
        kryo.register(ImageData.class);
        kryo.register(ImageDataStop.class);
        kryo.register(ImageSendResult.class);
    }

    public interface User32 extends Library {
        User32 INSTANCE = Native.load("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);
        void SystemParametersInfo (int one, int two, String s , int three);
    }
    public static void ChangeWallpaper(String path) {
        User32.INSTANCE.SystemParametersInfo(0x0014, 0, path, 1);
    }

    public static void main(String[] args) {
        new Main().run(args);
    }
}