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
		String method;
		
		if(name.equals("byte")) {
			method = "put";
		} else {
			method = new StringBuilder("put").appendCodePoint(Character.toUpperCase(name.codePointAt(0))).append(name.substring(name.offsetByCodePoints(0, 1))).toString();
		}
		
		return String.format("%s.%s(%sData)", bufferName, method, parameter.name());
	}

	@Override
	public String read(PacketParameter parameter, String bufferName) {
		String method;
		
		if(name.equals("byte")) {
			method = "get";
		} else {
			method = new StringBuilder("get").appendCodePoint(Character.toUpperCase(name.codePointAt(0))).append(name.substring(name.offsetByCodePoints(0, 1))).toString();
		}
		
		return String.format("%s.%s()", bufferName, method);
	}

	@Override
	public String size(PacketParameter parameter) {
		return String.format("NetworkUtils.%s_SIZE", name.toUpperCase());
	}
}
