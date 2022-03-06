package brownshome.netcode.annotationprocessor;

import brownshome.netcode.annotation.*;
import brownshome.netcode.annotation.converter.UseConverter;
import brownshome.netcode.annotationprocessor.parameter.*;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor14;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Stores all of the data to define a packet.
 */
public final class Packet {
	private static final Template TEMPLATE = VelocityHandler.instance().readTemplateFile("PacketTemplate");

	private final String name;
	private final int priority;
	private final boolean reliable;
	private final List<String> orderedBy;
	private final int minimumVersion;
	private final String executionExpression;
	private final PackageElement packageElement;

	private final List<PacketParameter> parameters;

	@SuppressWarnings("unchecked")
	public Packet(ExecutableElement element, ProcessingEnvironment env) throws PacketCompileException {
		Types types = env.getTypeUtils();
		Elements elements = env.getElementUtils();

		packageElement = env.getElementUtils().getPackageOf(element);

		//Extract all of the present annotations
		DefinePacket definePacket = element.getAnnotation(DefinePacket.class);
		minimumVersion = definePacket.since();

		var nameAnnotation = element.getAnnotation(brownshome.netcode.annotation.Name.class);
		if (nameAnnotation == null) {
			var methodName = element.getSimpleName().toString();

			// Default name
			name = methodName.substring(0, 1).toUpperCase() + methodName.substring(1) + "Packet";
		} else {
			name = nameAnnotation.value();
		}

		var orderingType = elements.getTypeElement(OrderedBy.class.getName()).asType();

		orderedBy = new ArrayList<>();
		orderedBy.add(name);

		for (var a : element.getAnnotationMirrors()) {
			if (a.getAnnotationType().equals(orderingType)) {
				for (var v : a.getElementValues().entrySet()) {
					switch (v.getKey().getSimpleName().toString()) {
						case "value" -> {
							var visitor = new SimpleAnnotationValueVisitor14<Boolean, Void>(false) {
								@Override
								public Boolean visitArray(List<? extends AnnotationValue> vals, Void o) {
									for (var v : vals) {
										if (!v.accept(this, o)) {
											env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Invalid @OrderedBy type '" + v + "'");
										}
									}

									return super.visitArray(vals, o);
								}

								@Override
								public Boolean visitType(TypeMirror t, Void o) {
									orderedBy.add(t.getKind().name());

									return true;
								}
							};

							v.getValue().accept(visitor, null);
						}

						case "self" -> {
							if (!(Boolean) v.getValue().getValue()) {
								orderedBy.remove(name);
							}
						}
					}
				}
			}
		}

		reliable = element.getAnnotation(Reliable.class) != null;

		Priority withPriority = element.getAnnotation(Priority.class);
		priority = withPriority == null ? 0 : withPriority.value();

		List<? extends VariableElement> parameters = element.getParameters();

		TypeMirror connectionType = types.getDeclaredType(
				elements.getTypeElement("brownshome.netcode.Connection"),
				types.getWildcardType(null, null),
				types.getWildcardType(null, null));
		TypeMirror versionType = types.getPrimitiveType(TypeKind.INT);

		this.parameters = new ArrayList<>();

		int con = -1, schemaIndex = -1;
		for (int i = 0; i < parameters.size(); i++) {
			VariableElement parameter = parameters.get(i);

			if (parameter.getAnnotation(ConnectionParam.class) != null) {
				if (!types.isSameType(parameter.asType(), connectionType)) {
					throw new PacketCompileException("Wrong type for the @ConnectionParam annotation", parameter);
				}

				if (con != -1) {
					throw new PacketCompileException("Two connection parameters cannot be defined", parameter);
				}

				con = i;
			} else if (parameter.getAnnotation(SchemaParam.class) != null) {
				if (!types.isSameType(parameter.asType(), versionType)) {
					throw new PacketCompileException("Wrong type for the @VersionParam annotation", parameter);
				}

				if (schemaIndex != -1) {
					throw new PacketCompileException("Two schema parameters cannot be defined", parameter);
				}

				schemaIndex = i;
			} else {
				TypeMirror childConverterType;

				try {
					UseConverter useConverter = parameter.getAnnotation(UseConverter.class);

					if(useConverter == null || useConverter.value() == null) {
						childConverterType = null;
					} else {
						throw new IllegalStateException("MirroredTypeException should have been thrown.");
					}
				} catch (MirroredTypeException mte) {
					childConverterType = mte.getTypeMirror();
				}

				ConverterExpression converter;
				try {
					converter = findConverter(parameter.asType(), childConverterType, env);
				} catch (PacketCompileException pce) {
					throw new PacketCompileException(pce.getMessage(), parameter);
				}

				this.parameters.add(new PacketParameter(parameter, converter));
			}
		}

		executionExpression = generateExecutionExpression(element, this.parameters, schemaIndex, con);
	}

	private ConverterExpression findConverter(TypeMirror parameter, TypeMirror baseConverter, ProcessingEnvironment env) throws PacketCompileException {
		Types types = env.getTypeUtils();

		TypeMirror string = env.getElementUtils().getTypeElement("java.lang.String").asType();
		TypeMirror list = env.getElementUtils().getTypeElement("java.util.ArrayList").asType();
		TypeMirror networkable = env.getElementUtils().getTypeElement("brownshome.netcode.annotation.converter.Networkable").asType();

		//List
		if (types.isAssignable(types.erasure(list), types.erasure(parameter))) {
			TypeMirror genericType = ((DeclaredType) parameter).getTypeArguments().get(0);

			ConverterExpression subExpression = findConverter(genericType, baseConverter, env);

			return new ListConverter(subExpression, genericType.toString());
		}

		//UseConverter specified
		if (baseConverter != null) {
			return new CustomConverter(baseConverter.toString());
		}

		//String
		if (types.isSameType(parameter, string)) {
			return new StringConverter();
		}

		//PrimitiveType
		if (parameter.getKind().isPrimitive()) {
			return new BasicTypeConverter(parameter.toString());
		}

		if (types.isAssignable(parameter, networkable)) {
			return new NetworkableConverter();
		}

		throw new PacketCompileException("No converter found for " + parameter);
	}

	private static final Set<Modifier> DISALLOWED_METHOD_MODIFIERS = Set.of(
			Modifier.ABSTRACT,
			Modifier.DEFAULT,
			Modifier.PRIVATE);

	private static final Set<Modifier> DISALLOWED_STATIC_MODIFIERS = Set.of(Modifier.PRIVATE);

	private static final Set<Modifier> DISALLOWED_INSTANCE_MODIFIERS = Set.of(
			Modifier.PRIVATE,
			Modifier.ABSTRACT);

	private static String generateExecutionExpression(ExecutableElement element, List<PacketParameter> parameters, int schemaIndex, int connectionIndex) throws PacketCompileException {
		StringBuilder args = new StringBuilder();

		int i = 0;
		for (var it = parameters.iterator(); ; i++) {
			if (i == schemaIndex) {
				args.append("schema");
			} else if (i == connectionIndex) {
				args.append("connection");
			} else {
				if (!it.hasNext())
					break;

				args.append(it.next().dataName());
			}

			boolean isLast = i >= schemaIndex && i >= connectionIndex && !it.hasNext();

			if (isLast) {
				break;
			} else {
				args.append(", ");
			}
		}
		
		Set<Modifier> modifiers = element.getModifiers();
		ElementKind kind = element.getKind();

		if (!Collections.disjoint(modifiers, DISALLOWED_METHOD_MODIFIERS)) {
			throw new PacketCompileException("Disallowed modifier", element);
		}

		Element enclosing = element.getEnclosingElement();

		if ((kind != ElementKind.CONSTRUCTOR && kind != ElementKind.METHOD)
				|| !(enclosing instanceof TypeElement enclosingClass)
				|| !enclosing.getKind().isClass()) {
			throw new PacketCompileException("This element is not allowed to be annotated", element);
		}

		boolean isConstructor = element.getKind() == ElementKind.CONSTRUCTOR;
		boolean isInstance = !modifiers.contains(Modifier.STATIC);

		if (isInstance) {
			if (!Collections.disjoint(DISALLOWED_INSTANCE_MODIFIERS, enclosingClass.getModifiers())) {
				throw new PacketCompileException("Disallowed modifier on enclosing class", element);
			}

			if (isConstructor) {
				return String.format("new %s(%s)", enclosingClass.getQualifiedName(), args);
			} else {
				return String.format("new %s().%s(%s)", enclosingClass.getQualifiedName(), element.getSimpleName(), args);
			}
		} else {
			if (!Collections.disjoint(DISALLOWED_STATIC_MODIFIERS, enclosingClass.getModifiers())) {
				throw new PacketCompileException("Disallowed modifier on enclosing class", element);
			}

			return String.format("%s.%s(%s)", enclosingClass.getQualifiedName(), element.getSimpleName(), args);
		}
	}

	public void writePacket(Writer writer, Schema schema) {
		VelocityContext context = new VelocityContext();
		context.put("packet", this);
		context.put("schema", schema);
		context.put("id", schema.allocateId());

		TEMPLATE.merge(context, writer);
	}

	public String name() {
		return name;
	}

	public int priority() {
		return priority;
	}

	public List<String> orderedBy() {
		return orderedBy;
	}

	public boolean reliable() {
		return reliable;
	}

	public int minimumVersion() {
		return minimumVersion;
	}

	public List<PacketParameter> parameters() {
		return parameters;
	}

	public String executionExpression() {
		return executionExpression;
	}

	public PackageElement packageElement() {
		return packageElement;
	}
}
