package brownshome.netcode.annotationprocessor.parameter;

import java.util.Collections;
import java.util.List;

/** This represents a parameter to a packet method. This only includes the
 * converted types, and not the connection or version number. */
public interface PacketParameter {
	default List<String> requiredImports() { return Collections.emptyList(); }
	
	ConverterExpression converter();
	
	/**
	 * private final ${type} ${name}Data;
	 **/
	String type();

	String name();
}
