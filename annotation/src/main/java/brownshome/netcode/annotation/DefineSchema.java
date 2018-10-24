/**
 * 
 */
package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.PACKAGE;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

@Documented
@Target(PACKAGE)
/**
 * This annotation is used to annotation a package that packets are defined in. All of the DefinePacket annotations in 
 * the package are defined to be part of that schema. The name of a schema must be a valid Java identifier, but it can
 * clash with other schema names.
 * 
 * This will generate a {Name}PacketSchema class in this package that should be added to the list of used schemas for
 * a connection.
 * 
 * The version number is used to negotiate the correct schema. Differing major versions will not be able to connection, while
 * differing minor versions will negotiate to the lowest matching minor version.
 * 
 * @author James Brown
 */
public @interface DefineSchema {
	String name();
	int major() default 0;
	int minor() default 0;
}
