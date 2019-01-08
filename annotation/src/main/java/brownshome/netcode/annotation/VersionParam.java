/**
 * 
 */
package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.PARAMETER;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

/**
 * This annotation is used to denote that the annotated parameter is the minor version that the schema is using.
 * @author James Brown
 *
 */
@Documented
@Target(PARAMETER)
public @interface VersionParam {

}
