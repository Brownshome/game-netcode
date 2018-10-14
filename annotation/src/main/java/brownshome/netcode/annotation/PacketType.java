package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Target;

@Target(METHOD)
/**
 * Indicates that this class represents a packet type. It must only be used on classes
 * that extend Packet and are concrete. The passed argument is the name of the packet.
 * @author James Brown
 */
public @interface PacketType {
	String name();
	
	int minimumVersion() default 0;
}
