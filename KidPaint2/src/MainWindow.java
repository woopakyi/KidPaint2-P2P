import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.fxml.FXML;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Parent;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.LinkedList;
import javafx.stage.FileChooser;
import java.util.*;
import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MainWindow {
    int lastRow = -1;
    int lastCow = -1;
    int rectStartRow = -1, rectStartCol = -1;
    int rectEndRow = -1, rectEndCol = -1;
    boolean isDrawingRect = false;

    int rectPreviewStartCol = -1;
    int rectPreviewStartRow = -1;
    int rectPreviewEndCol = -1;
    int rectPreviewEndRow = -1;
    boolean isDrawingRectPreview = false;

    final int NAME = 0;
    final int PIXELS = 1;
    final int TEXT = 2;
    final int STICKER = 3;
    final int CLEAR = 4;
    private String studioName;
    private boolean isAdmin;

    final int USERLIST = 5;
    final int KICK = 6;
    Stage userListStage = null;
    ListView<String> userListView;
    ObservableList<String> userListItems;

    Socket socket;
    DataInputStream in;
    DataOutputStream out;

    private UserListWindowController userListController;
    private ObservableList<String> connectedUsers = FXCollections.observableArrayList();

    @FXML
    TextArea areaMsg;

    @FXML
    TextField txtMsg;
    @FXML
    Button btnSend;

    @FXML private Button btnSave;
    @FXML private Button btnLoad;

    @FXML
    ChoiceBox<String> chbMode;

    @FXML
    Canvas canvas;

    @FXML
    Pane container;

    @FXML
    Pane panePicker;

    @FXML
    Pane paneColor;

    @FXML
    ScrollPane scrollPane;
    @FXML
    VBox messagePane;

    @FXML
    Region spacer;

    @FXML
    Button btnMoreColor;


    @FXML Button btnClear;

    @FXML Label lblUsername;
    @FXML Label lblPort;

    @FXML VBox stickerPickerRow;
    @FXML Button btnSticker;

    final String[] stickerFiles = {
            "sticker/sticker1.png",
            "sticker/sticker2.png",
            "sticker/sticker3.png",
            "sticker/sticker4.png",
            "sticker/sticker5.png",
            "sticker/sticker6.png",
            "sticker/sticker7.png"
    };
    final int STICKER_HEIGHT = 36;
    boolean stickerPickerVisible = false;
    List<Integer> pendingStickers = new ArrayList<>();


    ObservableList<Node> children;

    final int STICKER_LIMIT = 3;
    HBox stickerPreviewRow = new HBox(8);
    HBox stickerChooserRow = new HBox(10);
    List<Integer> stickerPreviewList = new ArrayList<>();
    boolean stickerLockMode = false;
    Button btnLocker, btnMinus, btnOK;

    private static final String STICKER_DIR = "sticker";

    public MainWindow(Stage stage, String username, String serverIp, int serverPort, String studioName, boolean isAdmin) throws IOException {
        this.isAdmin = isAdmin;
        this.username = username;
        this.studioName = studioName;  // ← add this field at top of class: private String studioName;

        socket = new Socket(serverIp, serverPort);  // ← use parameters
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());

        out.write(NAME);
        out.writeInt(username.getBytes("UTF-8").length);
        out.write(username.getBytes("UTF-8"));
        out.flush();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("mainWindowUI.fxml"));  // ← your working path
        loader.setController(this);
        Parent root = loader.load();
        Scene scene = new Scene(root);

        this.stage = stage;

        stage.setScene(scene);
        stage.setMinWidth(scene.getWidth());
        stage.setMinHeight(scene.getHeight());

        canvas.widthProperty().bind(container.widthProperty());
        canvas.heightProperty().bind(container.heightProperty());
        canvas.widthProperty().addListener(w -> onCanvasSizeChange());
        canvas.heightProperty().addListener(h -> onCanvasSizeChange());

        if (isAdmin) {
            lblUsername.setText("User: " + username + " (Admin)");
            lblUsername.setStyle("-fx-text-fill: blue;");
        } else {
            lblUsername.setText("User: " + username + " (Client)");
            lblUsername.setStyle(""); // Reset to default style
        }
        lblPort.setText("Studio: " + studioName);

        btnSend.setOnAction(event -> {
            String msgText = txtMsg.getText();
            try {
                sendPendingStickerBatch();
                sendText(msgText);
                txtMsg.clear();
            } catch (IOException ex) {}
        });

        stage.setOnCloseRequest(event -> quit());

        stage.show();
        initial();

        animationTimer.start();

        new Thread(this::receiveData).start();
    }

    @FXML
    void initialize() throws FileNotFoundException {
        children = messagePane.getChildren();

        // Always scroll to bottom when a message is added
        messagePane.heightProperty().addListener(event -> scrollPane.setVvalue(1.0));

        // Enter in txtMsg triggers send
        txtMsg.setOnKeyPressed(event -> {
            if ("ENTER".equals(event.getCode().toString())) {
                btnSend.fire();
            }
        });

        // Send text message (can be empty string)
        btnSend.setOnAction(event -> {
            String msgText = txtMsg.getText();
            try {
                sendPendingStickerBatch();
                sendText(msgText); // send to server
                txtMsg.clear();
            } catch (IOException ex) { /* handle or ignore */ }
        });

        stickerPickerRow.setVisible(false);
        stickerPickerRow.setManaged(false);

        btnSticker.setOnAction(event -> {
            // Just open the new Sticker & Emoji window
            openStickerEmojiWindow();
        });
        btnMoreColor.setOnAction(e -> openMoreColorPanel());

        btnClear.setOnAction(event -> {
            try {
                sendClear();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        lblUsername.setOnMouseClicked(e -> {
            if (isAdmin) {
                if (userListStage == null) {
                    initUserListWindow();
                }
                userListStage.show();
                userListStage.toFront();
            }
        });
    }

    private void initUserListWindow() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("UserListWindow.fxml"));
            Parent root = loader.load();
            userListController = loader.getController();

            // Set user list and studio name as before
            userListController.setUserList(connectedUsers);
            userListController.setStudioName(studioName);

            // Pass MainWindow and current username to controller for kick functionality
            userListController.setMainWindow(this, username);

            userListStage = new Stage();
            userListStage.setTitle("Users in Studio: " + studioName);
            userListStage.initOwner(stage);
            userListStage.initModality(Modality.NONE);
            userListStage.setScene(new Scene(root));
            userListStage.setResizable(false);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void openStickerEmojiWindow() {
        Platform.runLater(() -> {
            try {
                System.out.println("Opening StickerEmojiWindow...");
                FXMLLoader loader = new FXMLLoader(getClass().getResource("StickerEmojiWindow.fxml"));
                Parent root = loader.load();
                StickerEmojiWindowController controller = loader.getController();
                controller.setMainWindow(this);

                Stage stage = new Stage();
                stage.setTitle("Stickers & Emojis");
                stage.setScene(new Scene(root));
                stage.initOwner(this.stage);
                stage.show();
                System.out.println("StickerEmojiWindow opened successfully.");
            } catch (Exception ex) {
                System.err.println("Failed to open StickerEmojiWindow:");
                ex.printStackTrace();
            }
        });
    }

    void openMoreColorPanel() {
        Platform.runLater(() -> {
            try {
                System.out.println("Opening MoreColorPanel...");
                FXMLLoader loader = new FXMLLoader(getClass().getResource("MoreColorPanel.fxml"));
                Parent root = loader.load();
                MoreColorPanelController controller = loader.getController();
                controller.setMainWindow(this);

                Stage stage = new Stage();
                stage.setTitle("More Colors");
                stage.setScene(new Scene(root));
                stage.initOwner(this.stage);
                stage.show();
                System.out.println("MoreColorPanel opened successfully.");
            } catch (Exception ex) {
                System.err.println("Failed to open MoreColorPanel:");
                ex.printStackTrace();
            }
        });
    }

    private void showOrHideStickerPicker() throws FileNotFoundException {
        stickerPickerRow.getChildren().clear();
        if (stickerPickerVisible) {
            // When locked, add preview row first:
            if (stickerLockMode) setupStickerPreviewRow();
            // Always add chooser row:
            setupStickerChooserRow();
            stickerPickerRow.setVisible(true);
            stickerPickerRow.setManaged(true);
        } else {
            stickerPickerRow.setVisible(false);
            stickerPickerRow.setManaged(false);
        }
    }

       private void setupStickerPreviewRow() throws FileNotFoundException {
           stickerPreviewRow.getChildren().clear();

           for (int stickerId : stickerPreviewList) {
               try {
                   ImageView iv = new ImageView(new Image(new FileInputStream(stickerFiles[stickerId-1])));
                   iv.setFitHeight(STICKER_HEIGHT);
                   iv.setPreserveRatio(true);
                   iv.setOpacity(0.5);
                   stickerPreviewRow.getChildren().add(iv);
               } catch (FileNotFoundException e) {
                   System.err.println("Preview sticker not found: " + stickerFiles[stickerId-1]);
               }
           }
        // Right: [spacer][minus][OK]
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        stickerPreviewRow.getChildren().add(spacer);

        btnMinus = new Button("-");
        btnMinus.setStyle("-fx-background-color: red; -fx-text-fill: white;");
        btnMinus.setOnAction(e -> {
            if (!stickerPreviewList.isEmpty()) {
                stickerPreviewList.remove(stickerPreviewList.size() - 1);
                try { setupStickerPreviewRow(); } catch (FileNotFoundException ex) { ex.printStackTrace(); }
            }
        });

        btnOK = new Button("OK");
        btnOK.setStyle("-fx-background-color: #18cb34; -fx-text-fill: white;"); // green
        btnOK.setOnAction(e -> {
            try { sendStickerBatch(); } catch (IOException ex) { ex.printStackTrace();}
        });

        stickerPreviewRow.getChildren().add(btnMinus);
        stickerPreviewRow.getChildren().add(btnOK);

        stickerPreviewRow.setPadding(new Insets(0, 8, 0, 0));  // top,right,bottom,left

        // Add as FIRST ROW if not already there
        if (stickerPickerRow.getChildren().isEmpty() || stickerPickerRow.getChildren().get(0) != stickerPreviewRow) {
            if (!stickerPickerRow.getChildren().isEmpty())
                stickerPickerRow.getChildren().set(0, stickerPreviewRow);
            else
                stickerPickerRow.getChildren().add(0, stickerPreviewRow);
        }
    }

    private void setupStickerChooserRow() throws FileNotFoundException {
        stickerChooserRow.getChildren().clear();
        stickerChooserRow.setPadding(new Insets(0, 8, 0, 0));

        for (int i = 0; i < stickerFiles.length; i++) {
            final int stickerId = i + 1;  // IDs start at 1
            try {
                ImageView iv = new ImageView(new Image(new FileInputStream(stickerFiles[i])));
                iv.setFitHeight(STICKER_HEIGHT);
                iv.setPreserveRatio(true);
                iv.setSmooth(true);
                iv.setPickOnBounds(true);
                iv.setOnMouseClicked(e -> {
                    try {
                        stickerChooserClicked(stickerId);
                    } catch (Exception ex) { ex.printStackTrace(); }
                });
                stickerChooserRow.getChildren().add(iv);
            } catch (FileNotFoundException e) {
                System.err.println("Sticker not found: " + stickerFiles[i]);
            }
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        stickerChooserRow.getChildren().add(spacer);

        btnLocker = new Button(stickerLockMode ? "🔒" : "🔓");
        btnLocker.setOnAction(e -> {
            try { toggleLocker(); } catch (FileNotFoundException ex) { ex.printStackTrace(); }
        });
        stickerChooserRow.getChildren().add(btnLocker);

        // Always add as LAST ROW (index 1 if locked, index 0 if unlocked)
        int chooserIdx = stickerLockMode ? 1 : 0;
        if (stickerPickerRow.getChildren().size() <= chooserIdx ||
                stickerPickerRow.getChildren().get(chooserIdx) != stickerChooserRow) {
            if (stickerPickerRow.getChildren().size() > chooserIdx)
                stickerPickerRow.getChildren().set(chooserIdx, stickerChooserRow);
            else
                stickerPickerRow.getChildren().add(stickerChooserRow);
        }
    }



    void sendPendingStickerBatch() throws IOException {
        if (pendingStickers.isEmpty()) return;

        out.write(STICKER);
        byte[] nameBytes = username.getBytes("UTF-8");
        out.writeInt(nameBytes.length);
        out.write(nameBytes);
        out.writeInt(pendingStickers.size());
        for (int stickerId : pendingStickers) {
            out.writeInt(stickerId);
        }
        out.flush();

        pendingStickers.clear();
    }

    void toggleLocker() throws FileNotFoundException {
        stickerLockMode = !stickerLockMode;
        stickerPreviewList.clear();
        showOrHideStickerPicker();
    }

    void stickerChooserClicked(int stickerId) throws IOException, FileNotFoundException {
        if (!stickerLockMode) {
            sendSticker(stickerId);
        } else {
            if (stickerPreviewList.size() < STICKER_LIMIT) {
                stickerPreviewList.add(stickerId);
                setupStickerPreviewRow();
            }
            // else: ignore or show a warning
        }
    }

    void sendStickerBatch() throws IOException, FileNotFoundException {
        if (stickerPreviewList.isEmpty()) return;
        out.write(STICKER);
        byte[] nameBytes = username.getBytes("UTF-8");
        out.writeInt(nameBytes.length);
        out.write(nameBytes);
        out.writeInt(stickerPreviewList.size());
        for (int stickerId : stickerPreviewList)
            out.writeInt(stickerId);
        out.flush();
        stickerPreviewList.clear();
        setupStickerPreviewRow();
    }



    private Node messageNode(String text, boolean alignToRight, Color color) {
        HBox box = new HBox();
        box.setPadding(new Insets(10, 10, 10, 10));
        box.setFillHeight(true);
        Label label = new Label(text);
        label.setWrapText(true);
        label.setTextFill(color); // Set custom color
        if (alignToRight)
            box.setAlignment(Pos.BASELINE_RIGHT);
        else
            box.setAlignment(Pos.BASELINE_LEFT);
        box.getChildren().add(label);
        return box;
    }

    // You can keep the old function for default (black):
    private Node messageNode(String text, boolean alignToRight) {
        return messageNode(text, alignToRight, Color.BLACK); // default
    }

    void sendClear() throws IOException {
        out.write(CLEAR);
        out.flush();
    }

    String username;
    int numPixels = 20;
    Stage stage;
    AnimationTimer animationTimer;  //update UI
    int[][] data;
    double pixelSize, padSize, startX, startY;
    int selectedColorARGB;
    boolean isPenMode = true;
    LinkedList<Point> filledPixels = new LinkedList<Point>();

    class Point{
        int x, y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    void sendText (String text) throws IOException {
        out.write(TEXT);
        byte[] utf8Bytes = text.getBytes("UTF-8");
        out.writeInt(utf8Bytes.length);
        out.write(utf8Bytes);
        out.flush();

    }

    void sendSticker(int stickerId) throws IOException {
        out.write(STICKER);
        byte[] nameBytes = username.getBytes("UTF-8");
        out.writeInt(nameBytes.length);
        out.write(nameBytes);
        out.writeInt(1);        // batch size is 1
        out.writeInt(stickerId);
        out.flush();
    }

    public void appendEmojiToText(String emoji) {
        Platform.runLater(() -> {
            String oldText = txtMsg.getText();
            String newText = oldText + emoji;   // Always append to end
            txtMsg.setText(newText);
            txtMsg.positionCaret(newText.length());
            txtMsg.requestFocus();
        });
    }


    void receiveData() {
        try {
            while (true) {
                int type = in.read();
                if (type == -1) {
                    // Stream closed
                    System.out.println("Server closed connection or stream ended");
                    break; // exit loop
                }
                switch (type) {
                    case PIXELS:
                        receivePixels();
                        break;
                    case TEXT:
                        receiveText();
                        break;
                    case STICKER:
                        receiveSticker();
                        break;
                    case CLEAR:
                        receiveClear();
                        break;
                    case USERLIST:
                        receiveUserList();
                        break;
                    case KICK:
                        receiveKick();
                        break;
                    default:
                        System.err.println("Unknown message type: " + type);
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            System.out.println("Oops!");
        }

        // After exiting loop, close UI or exit:
        Platform.runLater(() -> {
            // Show info and quit or just quit quietly
            System.out.println("Connection closed, exiting");
            quit();
        });
    }

    void receiveKick() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING, "You have been kicked out of the studio by the Admin.", ButtonType.OK);
            alert.setTitle("Kicked Out");
            alert.showAndWait();
            quit(); // close window and exit app
        });
    }

    public void kickUser(String usernameToKick) {
        new Thread(() -> {
            try {
                out.write(KICK);
                byte[] userBytes = usernameToKick.getBytes("UTF-8");
                out.writeInt(userBytes.length);
                out.write(userBytes);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    void receiveUserList() throws IOException {
        int len = in.readInt();
        byte[] buf = new byte[len];
        in.readFully(buf);
        String msg = new String(buf, "UTF-8");

        // Expected format: "StudioName|user1;user2;user3;"
        String[] parts = msg.split("\\|", 2);

        String receivedStudioName = parts.length > 0 ? parts[0] : "";
        String usersPart = parts.length > 1 ? parts[1] : "";

        String[] users = usersPart.split(";");

        Platform.runLater(() -> {
            studioName = receivedStudioName;

            lblPort.setText("Studio: " + studioName); // Updates main window label

            connectedUsers.clear();
            for (String u : users) {
                if (!u.trim().isEmpty()) {
                    connectedUsers.add(u.trim());
                }
            }

            // Update UserListWindow if open
            if (userListController != null) {
                userListController.setStudioName(studioName);
            }

            if (userListStage != null) {
                userListStage.setTitle("Users in Studio: " + studioName);
            }
        });
    }

    void receiveSticker() throws IOException {
        int nameLen = in.readInt();
        byte[] nameBytes = new byte[nameLen];
        in.readFully(nameBytes);
        String sender = new String(nameBytes, "UTF-8");        int count = in.readInt();
        int[] stickerIds = new int[count];
        for (int i = 0; i < count; ++i) {
            stickerIds[i] = in.readInt();
        }
        boolean isMine = sender.equals(username);

        Platform.runLater(() -> {
            children.add(stickerMessageNode(sender, stickerIds, isMine));
            scrollPane.setVvalue(1.0);
        });
    }


    private Node stickerMessageNode(String sender, int[] stickerIds, boolean alignToRight) {
        HBox mainBox = new HBox();
        mainBox.setPadding(new Insets(10, 10, 10, 10));
        if (alignToRight)
            mainBox.setAlignment(Pos.BASELINE_RIGHT);
        else
            mainBox.setAlignment(Pos.BASELINE_LEFT);

        HBox contentBox = new HBox(8);
        Label label = new Label(sender + ":");
        contentBox.getChildren().add(label);

        final int STICKER_HEIGHT = 90; // px, matches preview picker

        for (int stickerId : stickerIds) {
            String imagePath = STICKER_DIR + "/sticker" + stickerId + ".png";
            ImageView imageView;
            try {
                if (new File(imagePath).exists()) {
                    imageView = new ImageView(new Image(new FileInputStream(imagePath)));
                } else {
                    imageView = new ImageView();
                    System.err.println("Sticker not found: " + imagePath);
                }
            } catch (IOException ex) {
                imageView = new ImageView();
                System.err.println("Error loading sticker: " + ex.getMessage());
            }
            imageView.setFitHeight(STICKER_HEIGHT);
            imageView.setPreserveRatio(true);

            contentBox.getChildren().add(imageView);
        }

        mainBox.getChildren().add(contentBox);
        return mainBox;
    }

    void receiveText() throws IOException {
        int size = in.readInt();
        byte[] buffer = new byte[size];
        in.readFully(buffer); // read fully before process

        String allText = new String(buffer, "UTF-8");

        // Detect system messages:
        boolean isSystemMessage = allText.endsWith("cleared the board.") || allText.contains("was kicked out of the studio.");

        Platform.runLater(() -> {
            if (isSystemMessage) {
                // Show system message in blue (or other style you want)
                children.add(messageNode(allText, false, Color.BLUE));
            } else {
                boolean isMine = allText.startsWith(username + ":");
                children.add(messageNode(allText, isMine));
            }
            scrollPane.setVvalue(1.0);
        });
    }

    void receivePixels() throws IOException {
        int color = in.readInt();
        int size = in.readInt();
        LinkedList<Point> list = new LinkedList<>();    //optional
        for (int i=0; i<size; i++) {
            int x = in.readInt();
            int y = in.readInt();
            list.add(new Point(x, y)); //optional for keeping a list of pixels

            data[y][x] = color; //update internal pixel data
        }
    }

    void receiveClear() {
        // Reset the drawing area
        Platform.runLater(() -> {
            for (int y = 0; y < numPixels; y++)
                for (int x = 0; x < numPixels; x++)
                    data[y][x] = 0; // or whatever "empty" means
            render(); // refresh canvas
        });
    }

    /**
     * Update canvas info when the window is resized
     */
    void onCanvasSizeChange() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        padSize = Math.min(w, h);
        startX = (w - padSize)/2;
        startY = (h - padSize)/2;
        pixelSize = padSize / numPixels;
    }

    /**
     * terminate this program
     */
    void quit() {
        System.out.println("Bye bye");
        stage.close();
        System.exit(0);
    }

    /**
     * Initialize UI components
     * @throws IOException
     */
    void initial() throws IOException {
        data = new int[numPixels][numPixels];

        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long l) {
                render();
            }
        };

        chbMode.setValue("Pen");

        canvas.setOnMousePressed(event -> {
            String mode = chbMode.getValue();
            isPenMode = mode.equals("Pen");
            filledPixels.clear();

            if (isPenMode) {
                penToData(event.getX(), event.getY());
            } else if (mode.equals("Eraser")) {
                eraserToData(event.getX(), event.getY());
            }else if (mode.equals("Rectangle")) {
                rectPreviewStartRow = (int) ((event.getY() - startY) / pixelSize);
                rectPreviewStartCol = (int) ((event.getX() - startX) / pixelSize);
                rectPreviewEndRow = rectPreviewStartRow;
                rectPreviewEndCol = rectPreviewStartCol;
                isDrawingRectPreview = true;
                render(); // to show initial preview
            }
        });

        canvas.setOnMouseDragged(event -> {
            String mode = chbMode.getValue();
            if (isPenMode) {
                penToData(event.getX(), event.getY());
            } else if (mode.equals("Eraser")) {
                eraserToData(event.getX(), event.getY());
            } else if (mode.equals("Rectangle") && isDrawingRectPreview) {
                rectPreviewEndRow = (int) ((event.getY() - startY) / pixelSize);
                rectPreviewEndCol = (int) ((event.getX() - startX) / pixelSize);
                render(); // repaint with updated preview
            }
        });

        canvas.setOnMouseReleased(event -> {
            String mode = chbMode.getValue();
            if (mode.equals("Rectangle") && isDrawingRectPreview) {
                // Actually draw rectangle to data
                drawRectangleToData(rectPreviewStartCol, rectPreviewStartRow, rectPreviewEndCol, rectPreviewEndRow);
                try { sendPixelChanges(); } catch (IOException e) { throw new RuntimeException(e); }
                isDrawingRectPreview = false;
                render(); // refresh (preview gone, rectangle committed)
            } else if (!isPenMode && mode.equals("Bucket")) {
                bucketToData(event.getX(), event.getY());
                try { sendPixelChanges(); } catch (IOException e) { throw new RuntimeException(e); }
            } else if (mode.equals("Pen") || mode.equals("Eraser")) {
                try { sendPixelChanges(); } catch (IOException e) { throw new RuntimeException(e); }
            }
            lastCow = lastRow = -1;
        });
        initColorMap();
    }

    void drawRectangleToData(int col1, int row1, int col2, int row2) {
        int minCol = Math.min(col1, col2);
        int maxCol = Math.max(col1, col2);
        int minRow = Math.min(row1, row2);
        int maxRow = Math.max(row1, row2);

        for (int c = minCol; c <= maxCol; ++c) {
            addRectPixel(c, minRow);
            addRectPixel(c, maxRow);
        }
        for (int r = minRow + 1; r < maxRow; ++r) {
            addRectPixel(minCol, r);
            addRectPixel(maxCol, r);
        }
    }
    void addRectPixel(int col, int row) {
        if (row >= 0 && row < numPixels && col >= 0 && col < numPixels) {
            data[row][col] = selectedColorARGB;
            filledPixels.add(new Point(col, row));
        }
    }

    void sendPixelChanges() throws IOException {
        String mode = chbMode.getValue();
        int colorToSend = mode.equals("Eraser") ? 0 : selectedColorARGB;
        out.write(PIXELS);
        out.writeInt(colorToSend);
        out.writeInt(filledPixels.size());
        for (Point p : filledPixels) {
            out.writeInt(p.x);
            out.writeInt(p.y);
        }
        out.flush();
    }

    /**
     * Initialize color map
     * @throws IOException
     */
    void initColorMap() throws IOException {
        Image image = new Image("file:color_map.png");
        ImageView imageView = new ImageView(image);

        imageView.setPreserveRatio(true);
        imageView.fitWidthProperty().bind(panePicker.widthProperty());  // Make sure it fills the pane
        imageView.fitHeightProperty().bind(panePicker.heightProperty());

        panePicker.getChildren().clear();
        panePicker.getChildren().add(imageView);

        // Initial pick at top left
        pickColor(image, 0, 0, image.getWidth(), image.getHeight());

        // Set mouse pick on imageView itself
        imageView.setOnMouseClicked(event -> {
            double viewWidth = imageView.getBoundsInLocal().getWidth();
            double viewHeight = imageView.getBoundsInLocal().getHeight();
            double imageWidth = image.getWidth();
            double imageHeight = image.getHeight();
            double scaleX = imageWidth / viewWidth;
            double scaleY = imageHeight / viewHeight;
            double x = event.getX();
            double y = event.getY();
            int imgX = (int)(x * scaleX);
            int imgY = (int)(y * scaleY);
            pickColor(image, imgX, imgY, imageWidth, imageHeight);
        });
    }

    /**
     * Pick a color from the color map image
     * @param image color map image
     * @param imgX x position in the image
     * @param imgY y position in the image
     * @param imageWidth the width of the image
     * @param imageHeight the height of the image
     */
    void pickColor(Image image, int imgX, int imgY, double imageWidth, double imageHeight) {
        if (imgX >= 0 && imgX < imageWidth && imgY >= 0 && imgY < imageHeight) {
            PixelReader reader = image.getPixelReader();

            selectedColorARGB = reader.getArgb(imgX, imgY);

            Color color = reader.getColor(imgX, imgY);
            paneColor.setStyle("-fx-background-color:#" + color.toString().substring(2));
        }
    }

    /**
     * Invoked when the Pen mode is used. Update sketch data array and store updated pixels in a list named filledPixels
     * @param mx mouse down/drag position x
     * @param my mouse down/drag position y
     */
    void penToData(double mx, double my) {
        if (mx > startX && mx < startX + padSize && my > startY && my < startY + padSize) {
            int row = (int) ((my - startY) / pixelSize);
            int col = (int) ((mx - startX) / pixelSize);
            if (row != lastRow || col != lastCow) {
                data[row][col] = selectedColorARGB;
                filledPixels.add(new Point(col, row));
                lastRow = row;
                lastCow = col;
            }
        }
    }

    void eraserToData(double mx, double my) {
        if (mx > startX && mx < startX + padSize && my > startY && my < startY + padSize) {
            int row = (int) ((my - startY) / pixelSize);
            int col = (int) ((mx - startX) / pixelSize);
            if (row != lastRow || col != lastCow) {
                data[row][col] = 0; // 0 == transparent
                filledPixels.add(new Point(col, row));
                lastRow = row;
                lastCow = col;
            }
        }
    }

    void bucketToData(double mx, double my) {
        if (mx > startX && mx < startX + padSize && my > startY && my < startY + padSize) {
            int row = (int) ((my - startY) / pixelSize);
            int col = (int) ((mx - startX) / pixelSize);
            paintArea(col, row);
        }
    }

    /**
     * Update the color of specific area
     * @param col position of the sketch data array
     * @param row position of the sketch data array
     */
    public void paintArea(int col, int row) {
        int oriColor = data[row][col];
        LinkedList<Point> buffer = new LinkedList<Point>();

        if (oriColor != selectedColorARGB) {
            buffer.add(new Point(col, row));

            while(!buffer.isEmpty()) {
                Point p = buffer.removeFirst();
                col = p.x;
                row = p.y;

                if (data[row][col] != oriColor) continue;

                data[row][col] = selectedColorARGB;
                filledPixels.add(p);

                if (col > 0 && data[row][col-1] == oriColor) buffer.add(new Point(col-1, row));
                if (col < data[0].length - 1 && data[row][col+1] == oriColor) buffer.add(new Point(col+1, row));
                if (row > 0 && data[row-1][col] == oriColor) buffer.add(new Point(col, row-1));
                if (row < data.length - 1 && data[row+1][col] == oriColor) buffer.add(new Point(col, row+1));
            }
        }
    }

    /**
     * Convert argb value from int format to JavaFX Color
     * @param argb
     * @return Color
     */
    Color fromARGB(int argb) {
        return Color.rgb(
                (argb >> 16) & 0xFF,
                (argb >> 8) & 0xFF,
                argb & 0xFF,
                ((argb >> 24) & 0xFF) / 255.0
        );
    }


    /**
     * Render the sketch data to the canvas
     */
    void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        double x = startX;
        double y = startY;

        gc.setStroke(Color.GRAY);
        for (int col = 0; col < numPixels; col++) {
            for (int row = 0; row < numPixels; row++) {
                gc.setFill(fromARGB(data[col][row]));
                gc.fillOval(x, y, pixelSize, pixelSize);
                gc.strokeOval(x, y, pixelSize, pixelSize);
                x += pixelSize;
            }
            x = startX;
            y += pixelSize;
        }

        // --- Rectangle preview ---
        String mode = chbMode.getValue();
        if ("Rectangle".equals(mode) && isDrawingRectPreview) {
            drawRectPreview(gc,
                    rectPreviewStartCol,
                    rectPreviewStartRow,
                    rectPreviewEndCol,
                    rectPreviewEndRow);
        }
    }

    /** Send every non-empty pixel to the server (grouped by colour). */
    private void broadcastFullCanvas() throws IOException {
        // 1. collect pixels per colour
        Map<Integer, LinkedList<Point>> groups = new HashMap<>();
        for (int y = 0; y < numPixels; y++) {
            for (int x = 0; x < numPixels; x++) {
                int c = data[y][x];
                if (c != 0) {
                    groups.computeIfAbsent(c, k -> new LinkedList<>()).add(new Point(x, y));
                }
            }
        }

        // 2. one PIXELS packet per colour
        for (Map.Entry<Integer, LinkedList<Point>> e : groups.entrySet()) {
            int colour = e.getKey();
            LinkedList<Point> list = e.getValue();

            out.write(PIXELS);
            out.writeInt(colour);
            out.writeInt(list.size());
            for (Point p : list) {
                out.writeInt(p.x);
                out.writeInt(p.y);
            }
            out.flush();
        }
    }

    @FXML
    private void onSave() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save sketch");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("KidPaint binary (*.kpb)", "*.kpb")
        );
        File f = fc.showSaveDialog(stage);
        if (f == null) return;

        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(f))) {
            dos.writeInt(numPixels);               // canvas size
            for (int y = 0; y < numPixels; y++) {
                for (int x = 0; x < numPixels; x++) {
                    dos.writeInt(data[y][x]);      // ARGB int
                }
            }
            showInfo("Saved to " + f.getName());
        } catch (IOException ex) {
            showError("Save failed: " + ex.getMessage());
        }
    }

    @FXML
    private void onLoad() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Load sketch");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("KidPaint binary (*.kpb)", "*.kpb")
        );
        File f = fc.showOpenDialog(stage);
        if (f == null) return;

        try (DataInputStream dis = new DataInputStream(new FileInputStream(f))) {
            int size = dis.readInt();
            if (size != numPixels) {
                throw new IOException("File is for a different canvas size (" + size + "×" + size + ")");
            }

            // read into a temporary array
            int[][] temp = new int[numPixels][numPixels];
            for (int y = 0; y < numPixels; y++) {
                for (int x = 0; x < numPixels; x++) {
                    temp[y][x] = dis.readInt();
                }
            }

            // ---- apply locally ----
            // Merge: only copy non-zero pixels from the file
            for (int y = 0; y < numPixels; y++) {
                for (int x = 0; x < numPixels; x++) {
                    if (temp[y][x] != 0) {
                        data[y][x] = temp[y][x];  // overwrite only where file has color
                    }
                }
            }
            Platform.runLater(this::render);

            // ---- broadcast to server (full canvas) ----
            broadcastFullCanvas();

            showInfo("Loaded & synchronized with all clients");

        } catch (IOException ex) {
            showError("Load failed: " + ex.getMessage());
        }
    }


    private void showInfo(String msg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, msg).showAndWait());
    }
    private void showError(String msg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, msg).showAndWait());
    }


    void drawRectPreview(GraphicsContext gc, int col1, int row1, int col2, int row2) {
        int minCol = Math.min(col1, col2);
        int maxCol = Math.max(col1, col2);
        int minRow = Math.min(row1, row2);
        int maxRow = Math.max(row1, row2);

        gc.setStroke(fromARGB(selectedColorARGB));
        gc.setLineWidth(2.0);

        double left = startX + minCol * pixelSize + pixelSize/2;
        double top = startY + minRow * pixelSize + pixelSize/2;
        double width = (maxCol - minCol) * pixelSize;
        double height = (maxRow - minRow) * pixelSize;

        gc.strokeRect(left, top, width, height);
        gc.setLineWidth(1.0); // reset line width
    }
}
