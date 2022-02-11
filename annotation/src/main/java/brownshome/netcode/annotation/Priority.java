package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Sets the priority of this packet. Higher priority packets are prioritised. Packet priority must be greater than
 * or equal to zero.
 **/
@Retention(SOURCE)
@Target(METHOD)
@Documented
public @interface Priority {
	/**
	 * An integer greater than or equal to zero stating the priority of this packet
	 * @return the priority
	 */
	int value();
}
