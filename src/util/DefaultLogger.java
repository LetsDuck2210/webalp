package util;

import java.lang.System.Logger;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.UnknownFormatConversionException;

public class DefaultLogger implements Logger {
	private final String name;
	public DefaultLogger(String name) {
		this.name = name;
	}
	
	@Override
	public String getName() {
		return name;
	}

	@Override
	public boolean isLoggable(Level level) {
		return true;
	}

	@Override
	public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
		System.out.println("[" + level + "] " + new Date() + ": " + msg);
	}

	@Override
	public void log(Level level, ResourceBundle bundle, String format, Object... params) {
		try {
			System.out.println(String.format("[" + level + "] " + new Date() + ": " + format, params));
		} catch(UnknownFormatConversionException e) {
			System.out.println("[" + level + "] " + new Date() + ": " + format);
		}
	}

}
