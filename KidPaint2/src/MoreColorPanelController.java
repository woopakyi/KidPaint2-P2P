import javafx.fxml.FXML;
import javafx.scene.control.ColorPicker;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class MoreColorPanelController {
    @FXML
    private ColorPicker colorPicker;

    private MainWindow mainWindow;

    public void setMainWindow(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        // Initialize color picker with current selected color
        Color current = mainWindow.fromARGB(mainWindow.selectedColorARGB);
        colorPicker.setValue(current);
    }

    @FXML
    public void initialize() {
        colorPicker.setOnAction(e -> {
            Color c = colorPicker.getValue();
            // Convert Color to ARGB int
            int argb = ((int)(c.getOpacity() * 255) << 24) |
                    ((int)(c.getRed() * 255) << 16) |
                    ((int)(c.getGreen() * 255) << 8) |
                    ((int)(c.getBlue() * 255));
            mainWindow.selectedColorARGB = argb;

            // Update color pane style in main window
            mainWindow.paneColor.setStyle("-fx-background-color:#" + c.toString().substring(2));
        });
    }

    @FXML
    public void onClose() {
        Stage stage = (Stage) colorPicker.getScene().getWindow();
        stage.close();
    }
}