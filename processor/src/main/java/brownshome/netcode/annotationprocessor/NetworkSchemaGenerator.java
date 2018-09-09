package brownshome.netcode.annotationprocessor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import brownshome.netcode.annotation.NetworkDirection;
import brownshome.netcode.annotation.PacketDeclaration;
import brownshome.netcode.annotation.PacketType;

public class NetworkSchemaGenerator extends AbstractProcessor {
	private Types typeUtils;
	private TypeElement packetType;
	private Filer filer;
	private VelocityEngine engine;
	private String schemaTemplate;
	
	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		
		typeUtils = processingEnv.getTypeUtils();
		packetType = processingEnv.getElementUtils().getTypeElement(processingEnv.getElementUtils().getModuleElement("brownshome.netcode"), "brownshome.netcode.Packet");
		filer = processingEnv.getFiler();
		
		engine = new VelocityEngine();
		engine.init();
		
		try(BufferedReader reader = new BufferedReader(new InputStreamReader(NetworkSchemaGenerator.class.getResourceAsStream("/velocity/NetworkSchemaTemplate.vm")))) {
			schemaTemplate = reader.lines().collect(Collectors.joining(System.lineSeparator()));
		} catch (IOException e) {
			processingEnv.getMessager().printMessage(Kind.ERROR, "Unable to read the schema template.");
		}
	}
	
	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		try {
			//For every class that has any of the annotations on it.
			//Check if it is a subclass of Packet.class, if not, error.
			//Check if it has a no argument constructor.

			Collection<PacketDeclaration> collection = new ArrayList<>();
			//Create the packet description.
			for(Element element : roundEnv.getElementsAnnotatedWith(PacketType.class)) {
				collection.add(createPacketTypeDescriptor(element));
			}

			if(collection.size() == 0) {
				return true;
			}
			
			String schemaName = "Game";
			try(Writer writer = filer.createSourceFile("brownshome.netcode.generated." + schemaName + "NetworkSchema").openWriter()) {
				VelocityContext context = new VelocityContext();
				context.put("schemaName", schemaName);
				context.put("types", collection);
				
				engine.evaluate(context, writer, "NetworkSchema", schemaTemplate);
			} catch (IOException e) {
				throw new PacketCompileException("Unable to generate network schema: " + e.getMessage());
			}
			
			return true;
		} catch(PacketCompileException pce) {
			pce.raiseError(processingEnv);
			return false;
		} catch(Exception e) {
			new PacketCompileException("Unknown error: " + e.getMessage()).raiseError(processingEnv);
			return false;
		}
	}

	private PacketDeclaration createPacketTypeDescriptor(Element element) throws PacketCompileException {
		if(element.getKind() != ElementKind.CLASS) {
			throw new PacketCompileException("Only classes can be annotated with @PacketType", element);
		}
		
		TypeElement classType = (TypeElement) element;
		
		if(classType.getModifiers().contains(Modifier.ABSTRACT)) {
			throw new PacketCompileException("Abstract classes cannot be annotated with @PacketType", element);
		}
		
		if(!doesExtendPacket(classType)) {
			throw new PacketCompileException("Only classes extending Packet can be annotated with @PacketType", element);
		}
		
		return new PacketDeclaration(null, 0, true, true, NetworkDirection.Sender.BOTH, "", classType.getQualifiedName().toString());
	}

	private boolean doesExtendPacket(TypeElement classType) {
		return typeUtils.isSubtype(classType.asType(), packetType.asType());
	}

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		return Set.of("*");
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latest();
	}
}
