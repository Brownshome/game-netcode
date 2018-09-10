module brownshome.netcode {
	exports brownshome.netcode;
	
	requires transitive brownshome.netcode.annotation;
	requires transitive java.compiler;
	
	requires java.logging;
	requires com.google.common;
}