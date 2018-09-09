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
/** Indicates that this packet type can be fragmented. Sending a packet larger than the MTU
 * that does not have this property will result in an error.
 * @author James Brown
 **/
public @interface CanFragment {

}
