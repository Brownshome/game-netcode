package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * This indicates that the method annotated is the endpoint of a packet.
 * This method may be an instance method or a static method. The method may throw NetworkExecption.
 * If the method is an instance method then the class must have a no-argument package (or more accessible) constructor.
 *
 * A class will be created {methodName}Packet, and {@code since} is the
 * minimum schema version that must be negotiated on for this packet to be usable. For IDs to remain usable, this number
 * must not be changed without also changing the major version number.
 *
 * @author James Brown
 */
@Target(METHOD)
@Retention(SOURCE)
@Documented
public @interface DefinePacket {
	/**
	 * Defines the earliest minor version this packet was in.
	 * @return the earliest minor version this packet is compatible with
	 */
	int since() default 0;
}
