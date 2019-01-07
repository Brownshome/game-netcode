package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

@Target(METHOD)
@Documented
/** Sets the priority of this packet. Higher priority packets are prioritised. Packet priority must be >= 0 */
public @interface WithPriority {
	int value();
}
