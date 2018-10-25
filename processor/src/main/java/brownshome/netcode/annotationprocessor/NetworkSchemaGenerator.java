package brownshome.netcode.annotationprocessor;

import java.io.Writer;
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

import brownshome.netcode.annotation.DefinePacket;
import brownshome.netcode.annotation.DefineSchema;

/**
 * This iterates through all methods that are annotated with DefinePacket
 * @author James Brown
 */
public class NetworkSchemaGenerator extends AbstractProcessor {
	private Filer filer;
	private static boolean firstPass = true;
	
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
			if(!firstPass)
				return true;
			
			firstPass = false;
			
			for(Element element : roundEnv.getElementsAnnotatedWith(DefineSchema.class)) {
				Schema schema = new Schema((PackageElement) element);
				
				Schema.addSchema(schema);
			}
			
			//Create the packet description.
			int i = 0;
			for(Element element : roundEnv.getElementsAnnotatedWith(DefinePacket.class)) {
				Packet packet = new Packet((ExecutableElement) element, processingEnv, i++);
				packet.schema().addPacket(packet);
				
				try(Writer writer = filer.createSourceFile(packet.packageName() + "." + packet.name() + "Packet").openWriter()) {
					packet.writePacket(writer);
				}
			}
			
			for(Schema schema : Schema.allSchema()) {
				try(Writer writer = filer.createSourceFile(schema.longName() + "Schema").openWriter()) {
					schema.writeSchema(writer);
				}
			}
			
			return false;
		} catch(PacketCompileException pce) {
			pce.raiseError(processingEnv);
			return false;
		} catch(Exception e) {
			throw new IllegalStateException("Unknown error", e);
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
