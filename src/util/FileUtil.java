package util;

import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.function.Supplier;

public class FileUtil {
	public static String sanitize(String path) {
		return path.replaceAll("([^a-zA-Z0-9.\\-\\/]|\\.\\.)", "_");
	}
	public static String loadTemplate(String file, Map<String, Supplier<String>> vars) throws IOException {
		var reader = new FileReader(file);
		
		var contents = "";
		while(reader.ready())
			contents += (char) reader.read();
		reader.close();
		
		if(vars != null)
			for(var var : vars.entrySet()) {
				contents = contents.replaceAll("\\$\\{" + var.getKey() + "\\}", var.getValue().get());
			}
		
		return contents;
	}
}
