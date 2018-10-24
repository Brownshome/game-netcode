package brownshome.netcode.annotationprocessor;

import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

import org.apache.velocity.VelocityContext;

import brownshome.netcode.annotation.DefinePacket;
import brownshome.netcode.annotation.DefineSchema;

/**
 * This iterates through all methods that are annotated with DefinePacket
 * @author James Brown
 */
public class NetworkSchemaGenerator extends AbstractProcessor {
	private Filer filer;
	
	private TypeElement converterElement;
	
	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		processingEnv.getMessager().printMessage(Kind.NOTE, "Started network schema generation.");
		
		super.init(processingEnv);
		
		filer = processingEnv.getFiler();
		
		processingEnv.getElementUtils().getTypeElement("");
	}
	
	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		try {
			Map<String, Schema> schemas = new HashMap<>();
			
			for(Element element : roundEnv.getElementsAnnotatedWith(DefineSchema.class)) {
				Schema schema = new Schema((PackageElement) element);
				
				schemas.put(schema.fullName(), schema);
			}
			
			//Create the packet description.
			for(Element element : roundEnv.getElementsAnnotatedWith(DefinePacket.class)) {
				Packet packet = new Packet((ExecutableElement) element);

				Schema schema = schemas.get(packet.schemaName());
				
				if (schema == null) {
					throw new PacketCompileException("Packets can only be defined in a package annotated with @PacketSchema", element);
				}
				
				schema.addPacket(packet);
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

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		return Set.of("*");
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latest();
	}
}
