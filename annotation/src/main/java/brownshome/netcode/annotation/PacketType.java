package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(CLASS)
@Target(TYPE)
/**
 * Indicates that this class represents a packet type. It must only be used on classes
 * that extend Packet and are concrete. The passed argument is the name of the packet.
 * @author James Brown
 */
public @interface PacketType {
	String value();
}
