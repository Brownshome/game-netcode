package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(CLASS)
@Target(TYPE)
/**
 * Indicates that this packet should be executed on the thread named by the annotation.
 * @author James Brown
 */
public @interface HandledBy {
	String value();
}
