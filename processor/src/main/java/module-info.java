module brownshome.netcode.processor {
	requires java.compiler;
	requires brownshome.netcode.annotation;
	requires org.apache.velocity.core;
	requires java.logging;
	
	provides javax.annotation.processing.Processor 
		with brownshome.netcode.annotationprocessor.NetworkSchemaGenerator;
}