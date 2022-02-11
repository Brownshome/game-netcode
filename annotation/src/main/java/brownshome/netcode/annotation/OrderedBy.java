package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Signals that this packet must be ordered with respect to the named packets. This means
 * that this packet will not be received before a packet of one of the named types that 
 * was sent after it.
 * 
 * The end of a handler for a packet that arrives 'first' establishes a happens-before relationship with the 'later' packet's handler starting.
 * 
 * This can be colloquially stated as 'this packet will not overtake any of the named packets.'
 * @author James Brown
 */
@Documented
@Retention(SOURCE)
@Target(METHOD)
public @interface OrderedBy {
	/**
	 * An array of classes that establish a happens-before relationship with this one
	 * @return an array of {@code Packet} classes
	 */
	Class<?>[] value() default {};

	boolean self() default true;
}
