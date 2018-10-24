package brownshome.netcode.annotationprocessor.parameter;

public class StringConverter implements ConverterExpression {
	@Override
	public String type() { return null; }

	@Override
	public String construct() { return null; }

	@Override
	public String write(PacketParameter parameter, String bufferName) {
		return String.format("NetworkUtils.writeString(%s, %sData)", bufferName, parameter.name());
	}

	@Override
	public String read(PacketParameter parameter, String bufferName) {
		return String.format("NetworkUtils.readString(%s)", bufferName);
	}

	@Override
	public String size(PacketParameter parameter) {
		return String.format("NetworkUtils.calculateSize(%sData)", parameter.name());
	}
}
