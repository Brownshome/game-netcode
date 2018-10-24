package brownshome.netcode.annotationprocessor.parameter;

public interface ConverterExpression {
	/** The type of the converter, or null if no type definition is needed. */
	String type();
	
	/** An expression for creating the converter. */
	String construct();
	
	/** An expression for writing a parameter to a byte buffer. */
	String write(PacketParameter parameter, String bufferName);
	
	/** An expression for reading a parameter from a byte buffer. */
	String read(PacketParameter parameter, String bufferName);
	
	/** An expression returning a NetworkObjectSize object representing the size
	 * of a parameter. */
	String size(PacketParameter parameter);
}
