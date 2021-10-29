package brownshome.netcode.annotationprocessor;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic.Kind;

import brownshome.netcode.annotation.DefinePacket;
import brownshome.netcode.annotation.DefineSchema;

/**
 * This iterates through all methods that are annotated with DefinePacket
 * @author James Brown
 */
public class NetworkSchemaGenerator extends AbstractProcessor {
	private final Map<PackageElement, Schema> packageMapping = new HashMap<>();
	private final List<Packet> packets = new ArrayList<>();

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		processingEnv.getMessager().printMessage(Kind.NOTE, "Started network schema generation.");
		
		super.init(processingEnv);
	}
	
	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		var filer = processingEnv.getFiler();
		var elements = processingEnv.getElementUtils();
		var messager = processingEnv.getMessager();

		TypeElement defineSchema = elements.getTypeElement(DefineSchema.class.getName());
		TypeElement definePacket = elements.getTypeElement(DefinePacket.class.getName());

		var packetsToWrite = new ArrayList<Packet>();

		if (annotations.contains(defineSchema)) {
			for (Element element : roundEnv.getElementsAnnotatedWith(defineSchema)) {
				if (!(element instanceof PackageElement packageElement)) {
					messager.printMessage(Kind.ERROR, "DefineSchema can only be applied to packages");
					continue;
				}

				var schema = new Schema(packageElement);
				packageMapping.put(packageElement, schema);

				for (var iterator = packets.listIterator(); iterator.hasNext(); ) {
					Packet p = iterator.next();
					if (schema.packageElement().equals(p.packageElement())) {
						schema.addPacket(p);
						packetsToWrite.add(p);
						iterator.remove();
					}
				}
			}
		}

		if (annotations.contains(definePacket)) {
			for (Element element : roundEnv.getElementsAnnotatedWith(definePacket)) {
				if (!(element instanceof ExecutableElement method)) {
					messager.printMessage(Kind.ERROR, "DefinePacket can only be applied to methods");
					continue;
				}

				Packet packet;
				try {
					packet = new Packet(method, processingEnv);
				} catch (PacketCompileException pce) {
					pce.raiseError(processingEnv);
					continue;
				}

				var schema = packageMapping.get(packet.packageElement());
				if (schema != null) {
					schema.addPacket(packet);
					packetsToWrite.add(packet);
				} else {
					packets.add(packet);
				}
			}
		}

		// Delay the packet file creation until all ids in this schema have been allocated
		for (var packet : packetsToWrite) {
			writePacket(packet, packageMapping.get(packet.packageElement()));
		}

		if (packets.isEmpty()) {
			for (var iterator = packageMapping.values().iterator(); iterator.hasNext(); ) {
				Schema schema = iterator.next();
				iterator.remove();

				try (Writer writer = filer.createSourceFile(schema.longName() + "Schema").openWriter()) {
					schema.writeSchema(writer);
				} catch (IOException ioex) {
					messager.printMessage(Kind.ERROR, "Unable to write out schema file: %s".formatted(ioex));
				}
			}
		}

		return true;
	}

	private void writePacket(Packet packet, Schema schema) {
		try (Writer writer = processingEnv.getFiler().createSourceFile(schema.packageName() + "." + packet.name() + "Packet").openWriter()) {
			packet.writePacket(writer, schema);
		} catch (IOException e) {
			processingEnv.getMessager().printMessage(Kind.ERROR, "Unable to write out packet file: %s".formatted(e));
		}
	}

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		return Set.of(DefineSchema.class.getName(), DefinePacket.class.getName());
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latest();
	}
}
