package util;

import java.lang.System.Logger;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.UnknownFormatConversionException;

import util.ColorUtil.Severity;
import static util.ColorUtil.rst;

public class DefaultLogger implements Logger {
	private final String name;
	private Level level;
	
	public DefaultLogger(String name) {
		this.name = name;
		level = Level.ALL;
	}
	
	public void setLevel(Level level) {
		this.level = level;
	}
	public Level getLevel() {
		return level;
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
		if(level.compareTo(this.level) < 0) return;
		System.out.println(Severity.from(level) + "[" + level + "] " + rst() + new Date() + ": " + msg);
	}

	@Override
	public void log(Level level, ResourceBundle bundle, String format, Object... params) {
		if(level.compareTo(this.level) < 0) return;
		try {
			System.out.println(String.format("%s[" + level + "] " + rst() + new Date() + ": " + format, Severity.from(level), params));
		} catch(UnknownFormatConversionException e) {
			System.out.println(Severity.from(level) + "[" + level + "] " + rst() + new Date() + ": " + format);
		}
	}

}
