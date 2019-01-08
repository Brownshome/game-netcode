package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Target;

/**
 * This indicates that the method annotated is the endpoint of a packet.
 * This method may be a constructor, an instance method or a static method. The method may throw NetworkExecption.
 * If the method is an instance method then the class must have a no-argument package (or more accessible) constructor.
 *
 * Name is the name of this packet, and minimumVersion is the minimum schema version that must be negotiated on for this
 * packet to be usable.
 *
 * @author James Brown
 */
@Target(METHOD)
public @interface DefinePacket {
	String name();
	
	int minimumVersion() default 0;
}
