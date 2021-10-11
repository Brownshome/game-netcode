package brownshome.netcode.annotationprocessor.parameter;

import javax.lang.model.element.VariableElement;

/** This represents a parameter to a packet method. This only includes the
 * converted types, and not the connection or version number. */
public class PacketParameter {
	private final String type, name;
	private final ConverterExpression converter;
	
	public PacketParameter(VariableElement element, ConverterExpression converter) {
		this(element.asType().toString(), element.getSimpleName().toString(), converter);
	}
	
	public PacketParameter(String type, String name, ConverterExpression converter) {
		this.type = type;
		this.name = name;
		this.converter = converter;
	}

	public final ConverterExpression converter() {
		return converter;
	}
	
	public final String type() {
		return type;
	}

	public String dataName() {
		return name() + "Data";
	}
	
	public String converterName() {
		return name() + "Converter";
	}

	public String sizeName() {
		return name() + "Size";
	}
	
	protected final String name() {
		return name;
	}
}
