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
		
		if(name.equals("Byte")) {
			method = "put";
		} else {
			method = "put" + name;
		}
		
		return String.format("%s.%s(%sData)", bufferName, method, parameter.name());
	}

	@Override
	public String read(PacketParameter parameter, String bufferName) {
		String method;
		
		if(name.equals("Byte")) {
			method = "get";
		} else {
			method = "get" + name;
		}
		
		return String.format("%s.%s()", bufferName, method);
	}

	@Override
	public String size(PacketParameter parameter) {
		return String.format("NetworkUtils.%s", name.toUpperCase());
	}
}
