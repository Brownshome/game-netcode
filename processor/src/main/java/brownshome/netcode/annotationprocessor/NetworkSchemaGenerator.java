package brownshome.netcode.annotationprocessor;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

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

import brownshome.netcode.annotation.*;

public class NetworkSchemaGenerator extends AbstractProcessor {
	private Types typeUtils;
	private TypeElement packetType;
	private Filer filer;
	
	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		
		typeUtils = processingEnv.getTypeUtils();
		packetType = processingEnv.getElementUtils().getTypeElement(processingEnv.getElementUtils().getModuleElement("brownshome.netcode"), "brownshome.netcode.Packet");
		filer = processingEnv.getFiler();
	}
	
	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		try {
			//For every class that has any of the annotations on it.
			//Check if it is a subclass of Packet.class, if not, error.
			//Check if it has a no argument constructor.

			//Create the packet description.
			for(Element element : roundEnv.getElementsAnnotatedWith(PacketType.class)) {
				PacketTypeDescription description = createPacketTypeDescriptor(element);
				
				try(Writer writer = filer.createSourceFile("brownshome.netcode.generated.NetworkSchema").openWriter()) {
					writer.write("package brownshome.netcode.generated; @javax.annotation.processing.Generated(\"brownshome.netcode.annotationprocessor.NetworkSchemaGenerator\") public class NetworkSchema { public int x; public String name = \"" + description.name + "\"; }");
				} catch (IOException e) {
					throw new PacketCompileException("Unable to generate network schema: " + e.getMessage());
				}
			}

			return true;
		} catch(PacketCompileException pce) {
			pce.raiseError(processingEnv);
			return false;
		}
	}

	private PacketTypeDescription createPacketTypeDescriptor(Element element) throws PacketCompileException {
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
		
		return new PacketTypeDescription(null, 0, true, true, NetworkDirection.Sender.BOTH, "");
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
