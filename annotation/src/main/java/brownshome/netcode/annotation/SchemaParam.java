/**
 * 
 */
package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * This annotation is used to denote that the annotated parameter is the schema.
 * @author James Brown
 */
@Documented
@Retention(SOURCE)
@Target(PARAMETER)
public @interface SchemaParam {

}
