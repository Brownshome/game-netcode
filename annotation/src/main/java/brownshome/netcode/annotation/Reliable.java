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
/** Indicates that this packet type must be sent reliably.
 * @author James Brown
 **/
public @interface Reliable {

}
