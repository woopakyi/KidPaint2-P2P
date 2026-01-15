import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.List;

public class SelectStudioDialog {
    @FXML ListView<StudioInfo> listViewStudios;
    @FXML Button btnJoin, btnCreate;
    @FXML Label lblNoStudios;  // Optional: show "No studios found" if empty

    private Stage stage;
    private StudioInfo selectedStudio = null;
    private boolean createNew = false;

    public SelectStudioDialog(String title, List<StudioInfo> studios) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("SelectStudioDialog.fxml"));  // Create this FXML
        loader.setController(this);
        Parent root = loader.load();
        Scene scene = new Scene(root);

        stage = new Stage();
        stage.setScene(scene);
        stage.setTitle(title);
        stage.setResizable(false);

        // Populate list
        listViewStudios.setItems(FXCollections.observableArrayList(studios));
        if (studios.isEmpty()) {
            lblNoStudios.setVisible(true);
            btnJoin.setDisable(true);
        }

        // Join button: enable only if selected
        btnJoin.disableProperty().bind(listViewStudios.getSelectionModel().selectedItemProperty().isNull());
        btnJoin.setOnAction(e -> {
            selectedStudio = listViewStudios.getSelectionModel().getSelectedItem();
            stage.close();
        });

        // Create button
        btnCreate.setOnAction(e -> {
            createNew = true;
            stage.close();
        });

        stage.showAndWait();
    }

    public StudioInfo getSelectedStudio() { return selectedStudio; }
    public boolean isCreateNew() { return createNew; }
}
