package brownshome.netcode.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Sets the name of a packet or schema class. By default, the method or package name is used, the first letter is capitalised
 * and Packet or Schema added.
 * <br>
 * For example {@code brownshome.netcode.udp} gets translated to {@code UdpSchema}
 */
@Documented
@Retention(SOURCE)
@Target({ METHOD, PACKAGE })
public @interface Name {
	/**
	 * The name of this packet or schema
	 * @return a Java class name
	 */
	String value();
}
