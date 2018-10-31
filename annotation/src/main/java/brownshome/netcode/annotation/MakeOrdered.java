package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Signals that this packet must be ordered with respect to the named packets. This means
 * that this packet will not be received before a packet of one of the named types that 
 * was sent after it.
 * 
 * Packets can only be ordered by packets in the same schema. The end of a handler for a packet that arrives 
 * 'first' establishes a happens-before relationship with the 'later' packet's handler starting.
 * 
 * This can be colloquially stated as 'this packet will not overtake any of the named packets.'
 * @author James Brown
 */
@Retention(SOURCE)
@Target({ METHOD, CONSTRUCTOR })
public @interface MakeOrdered {
	String[] value();
}
