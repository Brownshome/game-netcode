package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * This annotation is used to annotation a package that packets are defined in. All of the DefinePacket annotations in
 * the package are defined to be part of that schema. The name of a schema must be a valid Java identifier, but it can
 * clash with other schema names.
 *
 * This will generate a packet schema class in this package that should be added to the list of used schemas for
 * a connection.
 *
 * The version number is used to negotiate the correct schema. Differing major versions will not be able to connect, while
 * differing minor versions will negotiate to the lowest matching minor version.
 *
 * @author James Brown
 */
@Documented
@Retention(SOURCE)
@Target(PACKAGE)
public @interface DefineSchema {
	/**
	 * The major version of this schema
	 * @return a version
	 */
	int major() default 0;

	/**
	 * The minor version of this schema
	 * @return a version
	 */
	int minor() default 0;
}
