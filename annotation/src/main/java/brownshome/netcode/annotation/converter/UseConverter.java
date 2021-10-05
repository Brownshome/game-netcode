package brownshome.netcode.annotation.converter;

import static java.lang.annotation.ElementType.PARAMETER;

import java.lang.annotation.Documented;
import java.lang.annotation.Target;

/**
 * This annotation is used to define a custom converter for a type passed into a packet.
 * Parameters without this annotation must implement Convertible, or be an implicitly convertible type.
 *
 * All primitive types are implicitly convertible, as are Strings, and arrays and collections of any convertible (implicit or not) type.
 *
 * @author James Brown
 */
@Documented
@Target({ PARAMETER })
public @interface UseConverter {
	Class<? extends Converter<?>> value();
}
