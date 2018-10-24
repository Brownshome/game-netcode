package brownshome.netcode.annotationprocessor;

import java.io.IOException;
import java.util.Properties;

import org.apache.velocity.Template;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;

/** This is a singleton class that handles the velocity template system. */
public final class VelocityHandler {
	private static final VelocityHandler INSTANCE = new VelocityHandler();
	
	private final VelocityEngine engine;
	
	private VelocityHandler() {
		engine = new VelocityEngine();

		try {
			Properties prop = new Properties();
			prop.load(NetworkSchemaGenerator.class.getResourceAsStream("/velocity/properties.properties"));
			engine.init(prop);
		} catch (NullPointerException | IOException e) {
			throw new IllegalStateException("Unable to load the properties file");
		}
	}
	
	public final Template readTemplateFile(String name) {
		try {
			return engine.getTemplate(String.format("/velocity/%s.vm", name), "UTF-8");
		} catch(ParseErrorException | ResourceNotFoundException e) {
			throw new IllegalArgumentException("Unable to load the template file", e);
		}
	}
	
	public static VelocityHandler instance() {
		return INSTANCE;
	}
}
