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
 * This annotation is used to denote that the annotated parameter is the minor version that the schema is using.
 * @author James Brown
 *
 */
public @interface VersionParam {

}
