module brownshome.netcode {
	exports brownshome.netcode;
	exports brownshome.netcode.memory;
	exports brownshome.netcode.packets;
	exports brownshome.netcode.sizing;
	
	requires transitive brownshome.netcode.annotation;
	requires transitive java.compiler;
	
	requires java.logging;
	requires com.google.common;
}