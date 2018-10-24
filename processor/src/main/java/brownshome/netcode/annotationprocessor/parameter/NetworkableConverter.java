package brownshome.netcode.annotationprocessor.parameter;

public class NetworkableConverter implements ConverterExpression {
	@Override
	public String type() { return null; }

	@Override
	public String construct() { return null; }

	@Override
	public String write(PacketParameter parameter, String bufferName) {
		return String.format("%sData.write(%s)", parameter.name(), bufferName);
	}

	@Override
	public String read(PacketParameter parameter, String bufferName) {
		return String.format("new %s(%s)", parameter.type(), bufferName);
	}

	@Override
	public String size(PacketParameter parameter) {
		return String.format("new NetworkObjectSize(%sData)", parameter.name());
	}
}
