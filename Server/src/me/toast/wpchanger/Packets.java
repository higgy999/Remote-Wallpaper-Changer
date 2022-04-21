package me.toast.wpchanger;

import com.esotericsoftware.kryo.Kryo;

public class Packets {
    public static class ChangeBackgroundStart { String name; int width, height, length, type;}
    public static class ImageData { int index, pixel; }
    public static class ImageDataStop {}
    public static class ImageSendResult { boolean success; }

    public static void RegisterPackets(Kryo kryo) {
        kryo.register(ChangeBackgroundStart.class);
        kryo.register(ImageData.class);
        kryo.register(ImageDataStop.class);
        kryo.register(ImageSendResult.class);
    }
}
