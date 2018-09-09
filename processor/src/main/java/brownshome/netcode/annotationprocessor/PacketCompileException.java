package brownshome.netcode.annotationprocessor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;

/**
 * An exception thrown by improper use of any of the annotations
 * @author James Brown
 */
public class PacketCompileException extends Exception {
	private final Element element;
	
	public PacketCompileException(String message) {
		super(message);
		
		element = null;
	}
	
	public PacketCompileException(String message, Element element) {
		super(message);
		
		this.element = element;
	}
	
	public void raiseError(ProcessingEnvironment env) {
		if(element != null) {
			env.getMessager().printMessage(Kind.ERROR, getMessage(), element);
		} else {
			env.getMessager().printMessage(Kind.ERROR, getMessage());
		}
	}
}
