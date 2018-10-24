package brownshome.netcode.annotationprocessor.parameter;

public class ListConverter implements ConverterExpression {
	private final ConverterExpression elementConverter;
	
	public ListConverter(ConverterExpression elementConverter) {
		this.elementConverter = elementConverter;
	}
	
	@Override
	public String type() {
		return elementConverter.type();
	}

	@Override
	public String construct() {
		return elementConverter.construct();
	}

	@Override
	public String write(PacketParameter parameter, String bufferName) {
		return String.format("NetworkUtils.writeCollection(%2#s, %1#sData, (_%2#s, _%1#sData) -> %3#s)", 
				parameter.name(), bufferName, elementConverter.write(new InnerParameter(parameter), "_" + bufferName));
	}

	@Override
	public String read(PacketParameter parameter, String bufferName) {
		return String.format("NetworkUtils.readList(%1#s, (_%1#s) -> %2#s)", 
				bufferName, elementConverter.write(parameter, "_" + bufferName));
	}

	@Override
	public String size(PacketParameter parameter) {
		return String.format("NetworkUtils.calculateSize(%1#sData, _%1#s -> new NetworkObjectSize(%1#sConverter, %2#s))", 
				parameter.name(), elementConverter.size(new InnerParameter(parameter)));
	}
}

/** A small helper class that wraps a parameter in a way that does not alias with any other parameter names */
class InnerParameter implements PacketParameter {
	private final PacketParameter delegate;
	
	public InnerParameter(PacketParameter delegate) {
		this.delegate = delegate;
	}
	
	@Override
	public ConverterExpression converter() {
		return delegate.converter();
	}

	@Override
	public String type() {
		return delegate.type();
	}

	@Override
	public String name() {
		return "_" + delegate.name();
	}
	
}
