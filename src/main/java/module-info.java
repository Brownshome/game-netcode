module brownshome.netcode {
	exports brownshome.netcode;
	exports brownshome.netcode.memory;
	exports brownshome.netcode.udp;

	requires transitive brownshome.netcode.annotation;
	requires transitive java.compiler;
}