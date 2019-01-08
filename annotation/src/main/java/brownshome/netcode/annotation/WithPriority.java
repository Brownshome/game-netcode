package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

/** Sets the priority of this packet. Higher priority packets are prioritised. Packet priority must be >= 0 */
@Target(METHOD)
@Documented
public @interface WithPriority {
	int value();
}
