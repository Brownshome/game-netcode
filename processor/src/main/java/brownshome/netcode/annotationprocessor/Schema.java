package brownshome.netcode.annotationprocessor;

import brownshome.netcode.annotation.DefineSchema;
import brownshome.netcode.annotation.Name;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;

import javax.lang.model.element.PackageElement;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Holds the information needed to create a schema java file. */
public class Schema {
	private static final Template TEMPLATE = VelocityHandler.instance().readTemplateFile("SchemaTemplate");
	
	private final int minorVersion, majorVersion;
	private final String packageName, shortName;
	private final List<Packet> packetDefinitions;
	private final PackageElement element;

	private int freeId = 0;
	
	public Schema(PackageElement element) {
		DefineSchema schema = element.getAnnotation(DefineSchema.class);
		Name name = element.getAnnotation(Name.class);

		if (name != null) {
			shortName = name.value();
		} else {
			var packageName = element.getSimpleName().toString();
			shortName = packageName.substring(0, 1).toUpperCase() + packageName.substring(1) + "Schema";
		}

		this.element = element;
		minorVersion = schema.minor();
		majorVersion = schema.major();
		packageName = element.getQualifiedName().toString();
		packetDefinitions = new ArrayList<>();
	}
	
	public void addPacket(Packet packet) throws PacketCompileException {
		packetDefinitions.add(packet);

		if (packet.minimumVersion() > minorVersion) {
			throw new PacketCompileException(
					"Packet %s has a higher 'since' value than this schema. (%d > %d)".formatted(packet.name(),
							packet.minimumVersion(),
							minorVersion));
		}
	}
	
	public void writeSchema(Writer writer) {
		// Sort packets by their minimum version number and then their class name
		// This ensures that later additions of packets do not impact the order of earlier packet IDs
		packetDefinitions
				.sort(Comparator.comparingInt(Packet::minimumVersion)
						.thenComparing(Packet::name)
						.thenComparing(p -> p.packageElement().getQualifiedName().toString()));

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

	/** Allocates the next free id in this packet schema for the supplied packet. */
	public int allocateId() {
		return freeId++;
	}

	public PackageElement packageElement() {
		return element;
	}
}
