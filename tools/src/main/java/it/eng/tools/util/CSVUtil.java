package it.eng.tools.util;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class CSVUtil {

	public static List<String> toListString (String csvInput) {
		if (StringUtils.isNotBlank(csvInput)) {
			return Arrays.asList(StringUtils.splitPreserveAllTokens(csvInput, ","));
		}
		return null;
	}
}
