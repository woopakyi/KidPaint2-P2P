import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

public class UserListWindowController {
    @FXML private ListView<String> listViewUsers;
    @FXML private Label lblStudioName;
    @FXML private Button btnKick;

    private MainWindow mainWindow;  // ref to mainWindow to send kick command
    private String currentUsername; // to disable kicking yourself

    public void setUserList(ObservableList<String> users) {
        listViewUsers.setItems(users);
    }

    public void setStudioName(String studioName) {
        lblStudioName.setText("Studio: " + studioName);
    }

    public void setMainWindow(MainWindow mainWindow, String currentUsername) {
        this.mainWindow = mainWindow;
        this.currentUsername = currentUsername;

        // Enable kick button only if a user is selected and is not self
        listViewUsers.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || newV.equals(currentUsername)) {
                btnKick.setDisable(true);
            } else {
                btnKick.setDisable(false);
            }
        });

        btnKick.setOnAction(e -> {
            String selectedUser = listViewUsers.getSelectionModel().getSelectedItem();
            if (selectedUser != null && !selectedUser.equals(currentUsername)) {
                mainWindow.kickUser(selectedUser);
            }
        });
    }
}