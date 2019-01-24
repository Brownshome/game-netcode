package brownshome.lagatron;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.stage.Stage;

public class Lagatron extends Application {
	Stage stage;
	public static GUIController controller;
	
	@Override
	public void start(Stage primaryStage) throws Exception {
		controller = new GUIController(primaryStage);
	}

	public static void main(String[] args) {
		launch(args);
	}
}
