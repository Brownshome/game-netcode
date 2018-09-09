package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(CLASS)
@Target(TYPE)
@Documented
@Inherited
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
