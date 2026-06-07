package xuanmo.arcartxsuite.menu.config;

import java.util.regex.Pattern;

public record MenuCommandBinding(
    String menuId,
    String pattern,
    boolean regex,
    Pattern compiledRegex,
    String permission
) {

    public boolean matches(String commandLine) {
        if (commandLine == null) {
            return false;
        }
        if (regex) {
            return compiledRegex != null && compiledRegex.matcher(commandLine).matches();
        }
        return pattern.equalsIgnoreCase(commandLine)
            || commandLine.toLowerCase().startsWith(pattern.toLowerCase() + " ");
    }
}
