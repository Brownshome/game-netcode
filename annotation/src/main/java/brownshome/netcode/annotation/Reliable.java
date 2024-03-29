package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Indicates that this packet type must be sent reliably.
 * This does not defend from errors in the running of the handler code on the remote client. Server errors will still
 * be counted as a successful reception.
 * @author James Brown
 **/
@Retention(SOURCE)
@Target(METHOD)
@Documented
public @interface Reliable {

}
