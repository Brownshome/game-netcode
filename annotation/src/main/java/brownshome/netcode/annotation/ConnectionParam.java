/**
 * 
 */
package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.PARAMETER;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

@Documented
@Target(PARAMETER)
/**
 * This annotation is used to denote that the annotationed parameter is the connection that the packet was received on.
 * @author James Brown
 *
 */
public @interface ConnectionParam {

}
