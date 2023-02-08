package util;

import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class FileUtil {
	public static String sanitize(String path) {
		return path.replaceAll("([^a-zA-Z0-9.\\-\\/]|\\.\\.)", "_");
	}
	public static Optional<String> loadTemplate(String file, Map<String, Supplier<String>> vars) {
		String contents;
		try {
			var reader = new FileReader(file);
		
			contents = "";
			while(reader.ready())
				contents += (char) reader.read();
			reader.close();
			
			if(vars != null)
				for(var var : vars.entrySet()) {
					contents = contents.replaceAll("\\$\\{" + var.getKey() + "\\}", var.getValue().get());
			}
		} catch(IOException e) {
			return Optional.empty();
		}
		
		return Optional.of(contents);
	}
}
