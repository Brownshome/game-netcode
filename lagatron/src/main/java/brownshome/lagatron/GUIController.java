package brownshome.lagatron;

import java.io.IOException;
import java.net.*;

import javafx.beans.value.ChangeListener;
import javafx.collections.*;
import javafx.fxml.*;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.chart.XYChart.*;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class GUIController {
	@FXML CheckBox biDirectionalCheckBox;
	
	@FXML TextField serverAddressTextBox;
	@FXML TextField serverPortTextBox;
	@FXML TextField incommingPortTextBox;
	
	@FXML TextField MTULimitTextBox;
	@FXML TextField bandwidthLimitTextBox;
	@FXML TextField latencyTextBox;
	@FXML TextField jitterTextBox;
	
	@FXML Slider packetLossSlider;
	
	@FXML BarChart packetSizeChart;
	@FXML LineChart packetSendRateChart;
	@FXML LineChart bandwidthChart;
	
	@FXML Button startButton;
	
	InetSocketAddress destinationAddress;
	int incommingPort;
	int MTULimit;
	double bandwidthLimit;
	int latency;
	int jitter;
	
	Series<String, Number> uploadSizes;
	Series<String, Number> downloadSizes;
	
	public GUIController(Stage primaryStage) throws IOException {
		primaryStage.setTitle("Lagatron 9000");
		
		FXMLLoader loader = new FXMLLoader(GUIController.class.getResource("GUI.fxml"));
		loader.setController(this);
		
		primaryStage.setScene(new Scene(loader.load()));
		primaryStage.show();
		
		ChangeListener<Object> onChange = (a, b, c) -> validateInputs();
		
		serverAddressTextBox.textProperty().addListener(onChange);
		serverPortTextBox.textProperty().addListener(onChange);
		incommingPortTextBox.textProperty().addListener(onChange);
		
		MTULimitTextBox.textProperty().addListener(onChange);
		bandwidthLimitTextBox.textProperty().addListener(onChange);
		latencyTextBox.textProperty().addListener(onChange);
		jitterTextBox.textProperty().addListener(onChange);
		
		uploadSizes = new Series<String, Number>();
		downloadSizes = new Series<String, Number>();
		
		uploadSizes.setName("Upload");
		downloadSizes.setName("Download");
		
		packetSizeChart.getData().addAll(uploadSizes, downloadSizes);
	}
	
	public void validateInputs() {
		bandwidthLimit = 0;
		MTULimit = 0;
		latency = 0;
		jitter = 0;
		
		try {
			destinationAddress = new InetSocketAddress(serverAddressTextBox.getText(), Integer.parseInt(serverPortTextBox.getText()));
			incommingPort = Integer.parseInt(incommingPortTextBox.getText());
			
			if(!MTULimitTextBox.getText().isEmpty()) 
				MTULimit = Integer.parseInt(MTULimitTextBox.getText());
			if(!bandwidthLimitTextBox.getText().isEmpty()) 
				bandwidthLimit = Double.parseDouble(bandwidthLimitTextBox.getText());
			if(!latencyTextBox.getText().isEmpty()) 
				latency = Integer.parseInt(latencyTextBox.getText());
			if(!jitterTextBox.getText().isEmpty()) 
				jitter = Integer.parseInt(jitterTextBox.getText());
		} catch(IllegalArgumentException iae) {
			startButton.setDisable(true);
			return;
		}
		
		if(latency >= 0 && jitter >= 0 && incommingPort > 0 && incommingPort < 65565 && MTULimit >= 0 && 
				bandwidthLimit >= 0 && !destinationAddress.isUnresolved()) {
			startButton.setDisable(false);
		} else {
			startButton.setDisable(true);
		}
	}
	
	@FXML public void startPassthrough() {
		startButton.setDisable(true);
		
		boolean biDirectional = biDirectionalCheckBox.isSelected();
		double packetLoss = packetLossSlider.getValue() / 100.0;
		
		PassthroughHandler.startPassthrough(destinationAddress, incommingPort, bandwidthLimit, MTULimit, latency, jitter, 
				biDirectional, packetLoss);
		
		startButton.setText("Update Settings");
	}
	
	public void enableButton() {
		startButton.setDisable(false);
	}
}
