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
		return String.format("%1$sConverter.write(%2#s, %1$sData)", parameter.name(), bufferName);
	}

	@Override
	public String read(PacketParameter parameter, String bufferName) {
		return String.format("new %s().read(%s)", converterClass, bufferName);
	}

	@Override
	public String size(PacketParameter parameter) {
		return String.format("%1$sConverter.size(%1$sData)", parameter.name());
	}
}
