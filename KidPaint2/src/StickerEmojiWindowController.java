import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class StickerEmojiWindowController {
    @FXML
    private HBox stickerBox;

    @FXML
    private FlowPane emojiFlow;

    private MainWindow mainWindow;  // reference to main window controller

    // List of stickers (same as MainWindow)
    final String[] stickerFiles = {
            "sticker/sticker1.png",
            "sticker/sticker2.png",
            "sticker/sticker3.png",
            "sticker/sticker4.png",
            "sticker/sticker5.png",
            "sticker/sticker6.png",
            "sticker/sticker7.png"
    };
    final int STICKER_HEIGHT = 50;

    // Emoji list (you can expand this)
    final String[] emojis = {
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
            "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "😘",
            "😗", "😙", "😚", "😋", "😝", "😛", "💩", "🖕",
            "😎", "🙂‍↕️", "😏", "🙂‍↔️", "😒", "😞", "🥶", "😜",
            "😔", "😕", "😟", "🙁", "😣", "😖", "😫", "😩", "🥺",
            "😢", "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵",
            "😶‍🌫️", "😱", "😨", "😰", "😥", "🤑", "👻", "👹", "🖕",
            "👀", "🧠", "🙏", "🫶", "👽", "🤡", "😶",
            "😀", "😂", "😍", "😎", "😭", "👍", "🎉", "🔥",
            "🤔", "😴", "😡", "🤖", "👻", "🐱", "🐶", "🍕"



    };

    public void setMainWindow(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
    }

    @FXML
    public void initialize() {
        // Setup stickers
        for (int i = 0; i < stickerFiles.length; i++) {
            final int stickerId = i + 1;
            try {
                ImageView iv = new ImageView(new Image(new FileInputStream(stickerFiles[i])));
                iv.setFitHeight(STICKER_HEIGHT);
                iv.setPreserveRatio(true);
                iv.setSmooth(true);
                iv.setPickOnBounds(true);
                iv.setOnMouseClicked(e -> {
                    try {
                        mainWindow.sendSticker(stickerId);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
                stickerBox.getChildren().add(iv);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        // Setup emojis
        for (String emoji : emojis) {
            Button btn = new Button(emoji);
            btn.setStyle("-fx-font-size: 20px;");
            btn.setOnAction(e -> {
                mainWindow.appendEmojiToText(emoji);
            });
            emojiFlow.getChildren().add(btn);
        }
    }

    @FXML
    public void onClose() {
        Stage stage = (Stage) stickerBox.getScene().getWindow();
        stage.close();
    }
}