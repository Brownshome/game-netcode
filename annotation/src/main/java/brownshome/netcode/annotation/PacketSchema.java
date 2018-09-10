package brownshome.netcode.annotation;

import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Documented
@Retention(CLASS)
@Target(PACKAGE)
/**
 * This annotation indicates that this package contains packets.
 * @author James Brown
 */
public @interface PacketSchema {
	/**
	 * A human readable name for this schema, this must be a valid Java class name.
	 * These names can clash, and are only used for debugging purposes.
	 */
	String name();
	
	/**
	 * The major version number. Two schemas that don't match with this value are deemed incompatible.
	 */
	int major();
	
	/**
	 * The minor version number. Two schemas may by unmatched, in which case the schema with the lowest minor version
	 * is used.
	 */
	int minor();
}
