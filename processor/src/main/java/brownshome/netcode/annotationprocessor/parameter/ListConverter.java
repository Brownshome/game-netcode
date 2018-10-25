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
		InnerParameter innerParameter = new InnerParameter(parameter);
		
		return String.format("NetworkUtils.writeCollection(%s, %s, (_%1$s, %s) -> %s)", 
				bufferName, parameter.dataName(), innerParameter.dataName(), elementConverter.write(new InnerParameter(parameter), "_" + bufferName));
	}

	@Override
	public String read(PacketParameter parameter, String bufferName) {
		return String.format("NetworkUtils.readList(%1$s, (_%1$s) -> %2$s)", 
				bufferName, elementConverter.read(parameter, "_" + bufferName));
	}

	@Override
	public String size(PacketParameter parameter) {
		InnerParameter innerParameter = new InnerParameter(parameter);
		
		return String.format("NetworkUtils.calculateSize(%s, (%s) -> %s)", 
				parameter.dataName(), innerParameter.dataName(), elementConverter.size(new InnerParameter(parameter)));
	}
}

/** A small helper class that wraps a parameter in a way that does not alias with any other parameter names */
class InnerParameter extends PacketParameter {
	public InnerParameter(PacketParameter delegate) {
		super(delegate.type(), delegate.name(), delegate.converter());
	}
	
	@Override
	public String dataName() {
		// TODO Auto-generated method stub
		return "_" + super.dataName();
	}
}
