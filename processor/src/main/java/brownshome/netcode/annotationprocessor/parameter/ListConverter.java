package brownshome.netcode.annotationprocessor.parameter;

public class ListConverter implements ConverterExpression {
	private final ConverterExpression elementConverter;
	private final String elementType;

	/** A small helper class that wraps a parameter in a way that does not alias with any other parameter names */
	private final class ElementParameter extends PacketParameter {
		final PacketParameter delegate;

		ElementParameter(PacketParameter delegate) {
			super(elementType, "_" + delegate.name(), elementConverter);
			this.delegate = delegate;
		}

		@Override
		public String converterName() {
			return delegate.converterName();
		}
	}

	public ListConverter(ConverterExpression elementConverter, String elementType) {
		this.elementConverter = elementConverter;
		this.elementType = elementType;
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
		ElementParameter elementParameter = new ElementParameter(parameter);
		
		return String.format("NetworkUtils.writeCollection(%s, %s, (_%1$s, %s) -> %s)", 
				bufferName, parameter.dataName(), elementParameter.dataName(), elementParameter.converter().write(elementParameter, "_" + bufferName));
	}

	@Override
	public String read(PacketParameter parameter, String bufferName) {
		ElementParameter elementParameter = new ElementParameter(parameter);

		return String.format("NetworkUtils.readList(%1$s, (_%1$s) -> %2$s)", 
				bufferName, elementParameter.converter().read(elementParameter, "_" + bufferName));
	}

	@Override
	public String size(PacketParameter parameter) {
		ElementParameter elementParameter = new ElementParameter(parameter);
		
		return String.format("NetworkUtils.calculateSize(%s, (%s) -> %s)", 
				parameter.dataName(), elementParameter.dataName(), elementParameter.converter().size(elementParameter));
	}
}
