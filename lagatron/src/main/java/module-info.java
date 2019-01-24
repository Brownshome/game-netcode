module brownshome.lagatron {
	requires javafx.controls;
	requires javafx.fxml;
	
	exports brownshome.lagatron to javafx.graphics;
	opens brownshome.lagatron to javafx.fxml;
}