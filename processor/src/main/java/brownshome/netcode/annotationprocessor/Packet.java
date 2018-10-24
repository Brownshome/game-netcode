package brownshome.netcode.annotationprocessor;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import brownshome.netcode.annotation.CanFragment;
import brownshome.netcode.annotation.ConnectionParam;
import brownshome.netcode.annotation.DefinePacket;
import brownshome.netcode.annotation.HandledBy;
import brownshome.netcode.annotation.MakeReliable;
import brownshome.netcode.annotation.VersionParam;
import brownshome.netcode.annotation.WithDirection;
import brownshome.netcode.annotation.WithDirection.Direction;
import brownshome.netcode.annotation.WithPriority;
import brownshome.netcode.annotation.converter.Converter;
import brownshome.netcode.annotationprocessor.parameter.ConverterExpression;
import brownshome.netcode.annotationprocessor.parameter.PacketParameter;

/**
 * Stores all of the data to define a packet.
 */
public final class Packet {
	private final String name;
	private final int priority;
	private final boolean canFragment;
	private final boolean isReliable;
	private final Direction direction;
	private final String handledBy;
	private final Schema schema;
	private final int minimumVersion;
	private final String executionExpression;
	
	private final List<PacketParameter> parameters;
	
	/** Special parameters, -1 means that they are not included in the method call. */
	private final int versionIndex, connectionIndex;
	
	public Packet(ExecutableElement element, ProcessingEnvironment env) throws PacketCompileException {
		//Extract all of the present annotations
		executionExpression = generateMethodName(element);
		
		DefinePacket definePacket = element.getAnnotation(DefinePacket.class);
		minimumVersion = definePacket.minimumVersion();
		name = definePacket.name();
	
		canFragment = element.getAnnotation(CanFragment.class) != null;
		
		HandledBy handler = element.getAnnotation(HandledBy.class);
		handledBy = handler == null ? "default" : handler.value();
		
		isReliable = element.getAnnotation(MakeReliable.class) != null;
		
		WithDirection withDirection = element.getAnnotation(WithDirection.class);
		direction = handler == null ? Direction.BOTH : withDirection.value();
		
		WithPriority withPriority = element.getAnnotation(WithPriority.class);
		priority = handler == null ? 0 : withPriority.value();
		
		List<? extends VariableElement> parameters = element.getParameters();
		
		Types types = env.getTypeUtils();
		Elements elements = env.getElementUtils();
		TypeMirror connectionType = elements.getTypeElement("brownshome.netcode.Connection").asType();
		TypeMirror versionType = types.getPrimitiveType(TypeKind.INT);
		
		int con = -1, version = -1;
		for(int i = 0; i < parameters.size(); i++) {
			VariableElement parameter = parameters.get(i);
			
			if(parameter.getAnnotation(ConnectionParam.class) != null) {
				if(!types.isSameType(parameter.asType(), connectionType)) {
					throw new PacketCompileException("Wrong type for the @ConnectionParam annotation", parameter);
				}
			
				if(con != -1) {
					throw new PacketCompileException("Two connection parameters cannot be defined", parameter);
				}
				
				con = i;
			}
			
			if(parameter.getAnnotation(VersionParam.class) != null) {
				if(!types.isSameType(parameter.asType(), versionType)) {
					throw new PacketCompileException("Wrong type for the @VersionParam annotation", parameter);
				}
			
				if(version != -1) {
					throw new PacketCompileException("Two version parameters cannot be defined", parameter);
				}
				
				version = i;
			}
			
			ConverterExpression converter = findConverter(parameter, env);
		}
		
		versionIndex = version;
		connectionIndex = con;
	}

	private ConverterExpression findConverter(TypeMirror type, ProcessingEnvironment env) {
		//String
		
		//PrimitiveType
		
		//Collection
	}

	private static final Set<Modifier> DISALLOWED_METHOD_MODIFIERS = Set.of(
			Modifier.ABSTRACT,
			Modifier.DEFAULT,
			Modifier.PRIVATE);
	
	private static final Set<Modifier> DISALLOWED_STATIC_MODIFIERS = Set.of(Modifier.PRIVATE);
	
	private static final Set<Modifier> DISALLOWED_INSTANCE_MODIFIERS = Set.of(
			Modifier.PRIVATE,
			Modifier.ABSTRACT);
	
	private String generateMethodName(ExecutableElement element) throws PacketCompileException {
		Set<Modifier> modifiers = element.getModifiers();
		ElementKind kind = element.getKind();
		
		if(!Collections.disjoint(modifiers, DISALLOWED_METHOD_MODIFIERS)) {
			throw new PacketCompileException("Disallowed modifier", element);
		}
		
		Element enclosing = element.getEnclosingElement();
		
		if((kind != ElementKind.CONSTRUCTOR && kind != ElementKind.METHOD)
				|| !(enclosing instanceof TypeElement)
				|| !((TypeElement) enclosing).getKind().isClass()) {
			throw new PacketCompileException("This element is not allowed to be annotated", element);
		}
		
		TypeElement enclosingClass = (TypeElement) enclosing;
		
		boolean isConstructor = element.getKind() == ElementKind.CONSTRUCTOR;
		boolean isInstance = modifiers.contains(Modifier.STATIC);
		
		if(isInstance) {
			if(!Collections.disjoint(DISALLOWED_INSTANCE_MODIFIERS, enclosingClass.getModifiers())) {
				throw new PacketCompileException("Disallowed modifier on enclosing class", element);
			}
			
			if(isConstructor) {
				return String.format("new %s", enclosingClass.getQualifiedName());
			} else {
				return String.format("new %s().%s", enclosingClass.getQualifiedName(), element.getSimpleName());
			}
		} else {
			if(!Collections.disjoint(DISALLOWED_STATIC_MODIFIERS, enclosingClass.getModifiers())) {
				throw new PacketCompileException("Disallowed modifier on enclosing class", element);
			}
			
			return String.format("%s.%s", enclosingClass.getQualifiedName(), element.getSimpleName());
		}
	}

	public String name() {
		return name;
	}
	
	public int priority() {
		return priority;
	}
	
	public boolean canFragment() {
		return canFragment;
	}
	
	public boolean isReliable() {
		return isReliable;
	}
	
	public Direction direction() {
		return direction;
	}
	
	public String handledBy() {
		return handledBy;
	}
	
	public Schema schema() {
		return schema;
	}
	
	public String packageName() {
		return schema().packageName();
	}
	
	public String executionExpression() {
		return executionExpression;
	}
}
