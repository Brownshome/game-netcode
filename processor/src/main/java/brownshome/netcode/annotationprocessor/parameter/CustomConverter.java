package brownshome.netcode.annotationprocessor.parameter;

public class CustomConverter implements ConverterExpression {
	private final String converterClass;
	
	public CustomConverter(String converterClass) {
		this.converterClass = converterClass;
	}
	
	@Override
	public String type() {
		return converterClass;
	}

	@Override
	public String construct() {
		return String.format("new %s()", converterClass);
	}

	@Override
	public String write(PacketParameter parameter, String bufferName) {
		return String.format("%s.write(%s, %s)", parameter.converterName(), bufferName, parameter.dataName());
	}

	@Override
	public String read(PacketParameter parameter, String bufferName) {
		return String.format("%s.read(%s)", parameter.converterName(), bufferName);
	}

	@Override
	public String size(PacketParameter parameter) {
		return String.format("%s.size(%s)", parameter.converterName(), parameter.dataName());
	}
}
