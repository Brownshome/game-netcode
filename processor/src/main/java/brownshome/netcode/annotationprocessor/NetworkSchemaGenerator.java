package brownshome.netcode.annotationprocessor;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.exception.VelocityException;

import brownshome.netcode.annotation.CanFragment;
import brownshome.netcode.annotation.HandledBy;
import brownshome.netcode.annotation.NetworkDirection;
import brownshome.netcode.annotation.PacketSchema;
import brownshome.netcode.annotation.PacketType;
import brownshome.netcode.annotation.Priority;
import brownshome.netcode.annotation.Reliable;

public class NetworkSchemaGenerator extends AbstractProcessor {
	private Types typeUtils;
	private TypeElement packetType;
	private Filer filer;
	private VelocityEngine engine;
	private Template template;
	
	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		processingEnv.getMessager().printMessage(Kind.NOTE, "Started network schema generation.");
		
		super.init(processingEnv);
		
		typeUtils = processingEnv.getTypeUtils();
		packetType = processingEnv.getElementUtils()
				.getTypeElement(processingEnv.getElementUtils().getModuleElement("brownshome.netcode"), "brownshome.netcode.Packet");
		
		filer = processingEnv.getFiler();
		
		engine = new VelocityEngine();
		
		try {
			Properties prop = new Properties();
			prop.load(NetworkSchemaGenerator.class.getResourceAsStream("/velocity/properties.properties"));
			engine.init(prop);
		} catch(VelocityException e ) {
			processingEnv.getMessager().printMessage(Kind.ERROR, "Velocity Error: " + e.getMessage());
			return;
		} catch (NullPointerException | IOException e) {
			processingEnv.getMessager().printMessage(Kind.ERROR, "Unable to load the properties file: " + e.getMessage());
			return;
		}
		
		try {
			template = engine.getTemplate("/velocity/NetworkSchemaTemplate.vm");
		} catch(ParseErrorException | ResourceNotFoundException e) {
			processingEnv.getMessager().printMessage(Kind.ERROR, "Unable to load the template file: " + e.getMessage());
			return;
		}
	}
	
	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		try {
			//For every class that has any of the annotations on it.
			//Check if it is a subclass of Packet.class, if not, error.
			//Check if it has a no argument constructor.

			class Schema extends ArrayList<PacketDeclaration> {
				String name;
				String packageString;
				int major, minor;
				
				public Schema(PackageElement element) {
					super();
					
					PacketSchema schema = element.getAnnotation(PacketSchema.class);
					name = schema.name();
					minor = schema.minor();
					major = schema.major();
					packageString = element.getQualifiedName().toString();
				}
			}
			
			Map<String, Schema> schemas = new HashMap<>();
			
			for(Element element : roundEnv.getElementsAnnotatedWith(PacketSchema.class)) {
				Schema schema = new Schema((PackageElement) element);
				
				schemas.put(schema.name, schema);
			}
			
			//Create the packet description.
			for(Element element : roundEnv.getElementsAnnotatedWith(PacketType.class)) {
				PacketDeclaration decl = createPacketTypeDescriptor(element);

				Schema schema = schemas.get(decl.getSchema());
				
				if (schema == null) {
					throw new PacketCompileException("Packets can only be defined in a package annotated with @PacketSchema", element);
				}
				
				schema.add(decl);
			}

			for(Schema schema : schemas.values()) {
				try(Writer writer = filer.createSourceFile(schema.packageString + "." + schema.name + "NetworkSchema").openWriter()) {
					VelocityContext context = new VelocityContext();
					context.put("schemaname", schema.name);
					context.put("schemaminor", schema.minor);
					context.put("schemamajor", schema.major);
					context.put("schemapackage", schema.packageString);
					context.put("schemapackets", schema);

					template.merge(context, writer);
					
					processingEnv.getMessager().printMessage(Kind.NOTE, "Generated " + schema.packageString + "." + schema.name + "NetworkSchema");
				} catch (Exception e) {
					throw new PacketCompileException("Unable to generate network schema: " + e.getMessage());
				}
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
		
		PacketType packetType = classType.getAnnotation(PacketType.class);
		if(packetType == null)
			throw new PacketCompileException("No @PacketType found.", element);
		
		NetworkDirection direction = classType.getAnnotation(NetworkDirection.class);
		if(direction == null)
			throw new PacketCompileException("No @NetworkDirection found.", element);
		
		Priority priority = classType.getAnnotation(Priority.class);
		if(priority == null)
			throw new PacketCompileException("No @Priority found.", element);
		
		HandledBy handler = classType.getAnnotation(HandledBy.class);
		if(handler == null)
			throw new PacketCompileException("No @HandledBy found.", element);
		
		boolean canFragment = classType.getAnnotation(CanFragment.class) != null;
		boolean reliable = classType.getAnnotation(Reliable.class) != null;
		
		return new PacketDeclaration(findSchema(classType), packetType.value(), priority.value(), canFragment, 
				reliable, direction.value(), handler.value(), classType.getQualifiedName().toString());
	}

	private String findSchema(Element element) {
		if(element == null) {
			return null;
		}
		
		PacketSchema schema = element.getAnnotation(PacketSchema.class);
		
		if(schema != null)
			return schema.name();
		else
			return findSchema(element.getEnclosingElement());
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
