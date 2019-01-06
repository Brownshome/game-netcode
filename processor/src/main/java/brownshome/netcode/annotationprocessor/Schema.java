package brownshome.netcode.annotationprocessor;

import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.PackageElement;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;

import brownshome.netcode.annotation.DefineSchema;

/** Holds the information needed to create a schema java file. */
public class Schema {
	private static final Template TEMPLATE = VelocityHandler.instance().readTemplateFile("SchemaTemplate");
	private static final Map<String, Schema> PACKAGE_MAPPING = new HashMap<>();
	
	public static void addSchema(Schema schema) {
		PACKAGE_MAPPING.put(schema.packageName, schema);
	}
	
	public static Schema getSchema(PackageElement packageElement) {
		return PACKAGE_MAPPING.get(packageElement.getQualifiedName().toString());
	}
	
	private final int minorVersion, majorVersion;
	private final String packageName, shortName;
	private final List<Packet> packetDefinitions;
	
	private int freeId = 0;
	
	public Schema(PackageElement element) {
		DefineSchema schema = element.getAnnotation(DefineSchema.class);
		
		shortName = schema.name();
		minorVersion = schema.minor();
		majorVersion = schema.major();
		packageName = element.getQualifiedName().toString();
		packetDefinitions = new ArrayList<>();
	}
	
	public void addPacket(Packet packet) {
		packetDefinitions.add(packet);
	}
	
	public int idForPacket(String name) {
		for(int i = 0; i < packetDefinitions.size(); i++) {
			Packet packet = packetDefinitions.get(i);
			
			if(packet.name().equals(name)) {
				return i;
			}
		}
		
		throw new IllegalArgumentException(String.format("No packet matching '%s'", name));
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

	public static Collection<Schema> allSchema() {
		return PACKAGE_MAPPING.values();
	}

	/** Allocates the next free id in this packet schema for the supplied packet. */
	public int allocateId() {
		return freeId++;
	}
}
