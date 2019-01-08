package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Target;

/**
 * Indicates that this packet should be executed on the thread named by the annotation.
 * @author James Brown
 */
@Target(METHOD)
public @interface HandledBy {
	String value();
}
