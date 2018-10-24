package brownshome.netcode.annotationprocessor;

import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.PackageElement;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;

import brownshome.netcode.annotation.DefineSchema;

/** Holds the information needed to create a schema java file. */
public class Schema {
	private static final Template TEMPLATE = VelocityHandler.instance().readTemplateFile("NetworkSchema");
	
	private final int minorVersion, majorVersion;
	private final String packageName, shortName;
	private final List<Packet> packetDefinitions;
	
	public Schema(PackageElement element) {
		DefineSchema schema = element.getAnnotation(DefineSchema.class);
		
		shortName = schema.name();
		minorVersion = schema.minor();
		majorVersion = schema.major();
		packageName = element.getQualifiedName().toString();
		packetDefinitions = new ArrayList<>();
	}
	
	public void addPacket(Packet packet) {
		
	}
	
	public void writeSchema(Writer writer) {
		VelocityContext context = new VelocityContext();
		context.put("schema", this);

		TEMPLATE.merge(context, writer);
	}

	public String shortName() {
		return shortName;
	}
	
	public String longName() {
		return packageName + "." + shortName;
	}

	public String packageName() {
		return packageName;
	}
	
	public int minorVersion() {
		return minorVersion;
	}
	
	public int majorVersion() {
		return majorVersion;
	}
	
	public List<Packet> packetDefinitions() {
		return packetDefinitions;
	}
}
