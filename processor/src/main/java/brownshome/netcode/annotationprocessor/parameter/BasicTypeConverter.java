package brownshome.netcode.annotationprocessor.parameter;

public class BasicTypeConverter implements ConverterExpression {
	private final String name;
	
	public BasicTypeConverter(String name) {
		this.name = name;
	}
	
	@Override
	public String type() { return null; }

	@Override
	public String construct() { return null; }

	@Override
	public String write(PacketParameter parameter, String bufferName) {
		return switch (name) {
			case "byte" -> String.format("%s.put(%sData)", bufferName, parameter.name());
			case "boolean" -> String.format("%s.put(%sData ? (byte) 1 : (byte) 0)", bufferName, parameter.name());
			default -> {
				var method = new StringBuilder("put").appendCodePoint(Character.toUpperCase(name.codePointAt(0))).append(name.substring(name.offsetByCodePoints(0, 1))).toString();
				yield String.format("%s.%s(%sData)", bufferName, method, parameter.name());
			}
		};
	}

	@Override
	public String read(PacketParameter parameter, String bufferName) {
		return switch (name) {
			case "byte" -> String.format("%s.get()", bufferName);
			case "boolean" -> String.format("%s.get() != 0", bufferName);
			default -> {
				var method = new StringBuilder("get").appendCodePoint(Character.toUpperCase(name.codePointAt(0))).append(name.substring(name.offsetByCodePoints(0, 1))).toString();
				yield String.format("%s.%s()", bufferName, method);
			}
		};
	}

	@Override
	public String size(PacketParameter parameter) {
		return name.equals("boolean") ? "NetworkUtils.BYTE_SIZE" : String.format("NetworkUtils.%s_SIZE", name.toUpperCase());
	}
}
