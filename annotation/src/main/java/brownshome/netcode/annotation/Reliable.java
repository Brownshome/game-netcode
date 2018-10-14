package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

@Target(METHOD)
@Documented
/** Indicates that this packet type must be sent reliably.
 * @author James Brown
 **/
public @interface Reliable {

}
