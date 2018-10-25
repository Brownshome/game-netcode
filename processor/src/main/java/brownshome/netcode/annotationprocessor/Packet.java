package brownshome.netcode.annotationprocessor;

import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;

import brownshome.netcode.annotation.CanFragment;
import brownshome.netcode.annotation.ConnectionParam;
import brownshome.netcode.annotation.DefinePacket;
import brownshome.netcode.annotation.HandledBy;
import brownshome.netcode.annotation.MakeReliable;
import brownshome.netcode.annotation.VersionParam;
import brownshome.netcode.annotation.WithDirection;
import brownshome.netcode.annotation.WithDirection.Direction;
import brownshome.netcode.annotation.WithPriority;
import brownshome.netcode.annotation.converter.UseConverter;
import brownshome.netcode.annotationprocessor.parameter.BasicTypeConverter;
import brownshome.netcode.annotationprocessor.parameter.ConverterExpression;
import brownshome.netcode.annotationprocessor.parameter.CustomConverter;
import brownshome.netcode.annotationprocessor.parameter.ListConverter;
import brownshome.netcode.annotationprocessor.parameter.NetworkableConverter;
import brownshome.netcode.annotationprocessor.parameter.PacketParameter;
import brownshome.netcode.annotationprocessor.parameter.StringConverter;

/**
 * Stores all of the data to define a packet.
 */
public final class Packet {
	private static final Template TEMPLATE = VelocityHandler.instance().readTemplateFile("PacketTemplate");

	private final String name;
	private final int priority;
	private final boolean canFragment;
	private final boolean isReliable;
	private final Direction direction;
	private final String handledBy;
	private final Schema schema;
	private final int minimumVersion;
	private final String executionExpression;
	private final int id;

	private final List<PacketParameter> parameters;

	/** Special parameters, -1 means that they are not included in the method call. */
	private final int versionIndex, connectionIndex;

	public Packet(ExecutableElement element, ProcessingEnvironment env, int id) throws PacketCompileException {
		this.id = id;
		
		//Extract all of the present annotations
		

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

		TypeMirror connectionType = types.getDeclaredType(elements.getTypeElement("brownshome.netcode.Connection"), types.getWildcardType(null, null));
		TypeMirror versionType = types.getPrimitiveType(TypeKind.INT);

		this.parameters = new ArrayList<>();

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
			} else if(parameter.getAnnotation(VersionParam.class) != null) {
				if(!types.isSameType(parameter.asType(), versionType)) {
					throw new PacketCompileException("Wrong type for the @VersionParam annotation", parameter);
				}

				if(version != -1) {
					throw new PacketCompileException("Two version parameters cannot be defined", parameter);
				}

				version = i;
			} else {
				TypeMirror childConverterType;

				try {
					UseConverter useConverter = parameter.getAnnotation(UseConverter.class);

					if(useConverter == null || useConverter.value() == null) {
						childConverterType = null;
					} else {
						throw new IllegalStateException("MirroredTypeException should have been thrown.");
					}
				} catch(MirroredTypeException mte) {
					childConverterType = mte.getTypeMirror();
				}

				ConverterExpression converter = findConverter(parameter.asType(), childConverterType, env);
				this.parameters.add(new PacketParameter(parameter, converter));
			}
		}

		versionIndex = version;
		connectionIndex = con;

		//Find schema
		PackageElement packageElement = elements.getPackageOf(element);
		Schema tmp = Schema.getSchema(packageElement);

		while(tmp == null) {
			Element e = packageElement.getEnclosingElement();

			if(!(e instanceof PackageElement)) {
				throw new PacketCompileException("No schema definition found", element);
			}

			packageElement = (PackageElement) e;

			tmp = Schema.getSchema(packageElement);
		}

		schema = tmp;
		
		executionExpression = generateExecutionExpression(element, this.parameters, versionIndex, connectionIndex);
	}

	private ConverterExpression findConverter(TypeMirror parameter, TypeMirror baseConverter, ProcessingEnvironment env) {
		Types types = env.getTypeUtils();

		TypeMirror string = env.getElementUtils().getTypeElement("java.lang.String").asType();
		TypeMirror list = env.getElementUtils().getTypeElement("java.util.List").asType();
		TypeMirror networkable = env.getElementUtils().getTypeElement("brownshome.netcode.annotation.converter.Networkable").asType();

		//String
		if(types.isSameType(parameter, string)) {
			return new StringConverter();
		}

		//PrimitiveType
		if(parameter.getKind().isPrimitive()) {
			return new BasicTypeConverter(parameter.toString());
		}

		//List
		if(types.isSameType(types.erasure(parameter), types.erasure(list))) {
			TypeMirror genericType = ((DeclaredType) parameter).getTypeArguments().get(0);

			ConverterExpression subExpression = findConverter(genericType, baseConverter, env);

			return new ListConverter(subExpression);
		}

		if(types.isAssignable(parameter, networkable)) {
			return new NetworkableConverter();
		}

		return new CustomConverter(baseConverter.toString());
	}

	private static final Set<Modifier> DISALLOWED_METHOD_MODIFIERS = Set.of(
			Modifier.ABSTRACT,
			Modifier.DEFAULT,
			Modifier.PRIVATE);

	private static final Set<Modifier> DISALLOWED_STATIC_MODIFIERS = Set.of(Modifier.PRIVATE);

	private static final Set<Modifier> DISALLOWED_INSTANCE_MODIFIERS = Set.of(
			Modifier.PRIVATE,
			Modifier.ABSTRACT);

	private static String generateExecutionExpression(ExecutableElement element, List<PacketParameter> parameters, int versionIndex, int connectionIndex) throws PacketCompileException {
		StringBuilder args = new StringBuilder();
		
		int i = 0;
		for(var it = parameters.iterator(); ; i++) {
			if(i == versionIndex) {
				args.append("minorVersion");
			} else if(i == connectionIndex) {
				args.append("connection");
			} else {
				args.append(it.next().dataName());
			}
			
			boolean isLast = i >= versionIndex && i >= connectionIndex && !it.hasNext();
			
			if(isLast) {
				break;
			} else {
				args.append(", ");
			}
		}
		
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
		boolean isInstance = !modifiers.contains(Modifier.STATIC);

		if(isInstance) {
			if(!Collections.disjoint(DISALLOWED_INSTANCE_MODIFIERS, enclosingClass.getModifiers())) {
				throw new PacketCompileException("Disallowed modifier on enclosing class", element);
			}

			if(isConstructor) {
				return String.format("new %s(%s)", enclosingClass.getQualifiedName(), args);
			} else {
				return String.format("new %s().%s(%s)", enclosingClass.getQualifiedName(), element.getSimpleName(), args);
			}
		} else {
			if(!Collections.disjoint(DISALLOWED_STATIC_MODIFIERS, enclosingClass.getModifiers())) {
				throw new PacketCompileException("Disallowed modifier on enclosing class", element);
			}

			return String.format("%s.%s(%s)", enclosingClass.getQualifiedName(), element.getSimpleName(), args);
		}
	}

	public void writePacket(Writer writer) {
		VelocityContext context = new VelocityContext();
		context.put("packet", this);

		TEMPLATE.merge(context, writer);
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
	
	public int id() {
		return id;
	}
	
	public List<PacketParameter> parameters() {
		return parameters;
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
