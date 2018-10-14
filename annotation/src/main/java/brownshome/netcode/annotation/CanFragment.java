package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

@Target(METHOD)
@Documented
/** Indicates that this packet type can be fragmented. Sending a packet larger than the MTU
 * that does not have this property will result in an error.
 * @author James Brown
 **/
public @interface CanFragment {

}
