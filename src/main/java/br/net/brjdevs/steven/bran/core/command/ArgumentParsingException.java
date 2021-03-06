package br.net.brjdevs.steven.bran.core.command;

public class ArgumentParsingException extends Throwable {
	
	public ArgumentParsingException(String className, String given) {
		super("Given invalid argument \"" + given + "\" while expecting \"" + className + "\"");
	}
	
	public ArgumentParsingException(Class<?> invalid) {
		super("Expected invalid argument type \"" + invalid.getSimpleName() + "\"");
	}
	
	public ArgumentParsingException(String msg) {
		super(msg);
	}
	
	public ArgumentParsingException() {
		super();
	}
}
