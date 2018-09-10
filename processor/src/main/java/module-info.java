module brownshome.netcode.processor {
	requires java.compiler;
	requires brownshome.netcode.annotation;
	requires velocity.engine.core;
	requires java.logging;
	
	provides javax.annotation.processing.Processor 
		with brownshome.netcode.annotationprocessor.NetworkSchemaGenerator;
}