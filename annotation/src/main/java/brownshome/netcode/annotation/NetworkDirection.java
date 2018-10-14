package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

@Target(METHOD)
@Documented
/**
 * Indicates the entity that will send this packet. Attempting to
 * violate this will result in errors.
 * @author James Brown
 */
public @interface NetworkDirection {
	public enum Sender {
		CLIENT, SERVER, BOTH
	}

	Sender value();
}
