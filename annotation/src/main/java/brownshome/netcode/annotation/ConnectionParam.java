/**
 * 
 */
package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.PARAMETER;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

/**
 * This annotation is used to denote that the annotationed parameter is the connection that the packet was received on.
 * @author James Brown
 *
 */
@Documented
@Target(PARAMETER)
public @interface ConnectionParam {

}
