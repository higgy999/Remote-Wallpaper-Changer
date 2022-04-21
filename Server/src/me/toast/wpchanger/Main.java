package me.toast.wpchanger;

import com.esotericsoftware.kryonet.*;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class Main extends Application {

    public static Server SERVER;

    public static Label backgroundsLabel;
    public static Label info;
    public static Button changeBackground;
    public static TextField backgroundName;
    public static TextArea backgrounds;

    public static Listener LISTENER = new Listener() {
        public void connected(Connection connection) {
            Platform.runLater(() -> info.setText("Client connected from: " + connection.getRemoteAddressTCP().getAddress().toString().substring(1)));
        }
        public void disconnected(Connection connection) {
            Platform.runLater(() -> info.setText("Client disconnected!"));
        }
        public void received(Connection connection, Object object) {
            if (object instanceof Packets.ImageSendResult packet) {
                if (packet.success) {
                    Platform.runLater(() -> info.setText("Background change succeeded."));
                } else {
                    Platform.runLater(() -> info.setText("Failed to change background!"));
                }
            }
        }
    };

    public static void main(String[] args) throws IOException {
        SERVER = new Server(1000000 * 128, 1000000 * 128);
        Packets.RegisterPackets(SERVER.getKryo());
        Thread thread = new Thread(SERVER); thread.start();
        SERVER.bind(54556);
        SERVER.addListener(LISTENER);

        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("Wallpaper Changer - Server");

        changeBackground = new Button("Change Background");
        changeBackground.setOnAction(e -> {
            new Thread(new Runnables.SendChangeBackground()).start();
            changeBackground.setDisable(true);
        });

        backgroundName = new TextField();
        backgroundName.setPromptText("Background Filename");
        backgroundName.setPrefWidth(200);

        backgrounds = new TextArea();
        backgrounds.setPrefWidth(400);
        backgrounds.setPrefHeight(600);
        backgrounds.setEditable(false);

        Runnables.CheckForFiles.INSTANCE.updateBackgroundAreaText();
        new Thread(Runnables.CheckForFiles.INSTANCE).start();

        backgroundsLabel = new Label("Backgrounds: ");
        info = new Label();

        HBox buttonAndInputBox = new HBox();
        buttonAndInputBox.setAlignment(Pos.CENTER);
        buttonAndInputBox.getChildren().addAll(backgroundName, changeBackground);

        HBox centerLabelInfo = new HBox();
        centerLabelInfo.setAlignment(Pos.CENTER);
        centerLabelInfo.getChildren().addAll(info);

        VBox backgroundBox = new VBox();
        backgroundBox.setAlignment(Pos.CENTER);
        backgroundBox.getChildren().addAll(backgroundsLabel, backgrounds);


        BorderPane borderPane = new BorderPane();
        borderPane.setTop(buttonAndInputBox);
        borderPane.setCenter(centerLabelInfo);
        borderPane.setBottom(backgroundBox);

        Scene scene = new Scene(borderPane, 720, 720);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        SERVER.stop();
        Runnables.CheckForFiles.INSTANCE.keepRunningCheck = false;
        Runnables.CheckForFiles.INSTANCE.watcher.close();
        super.stop();
    }
}