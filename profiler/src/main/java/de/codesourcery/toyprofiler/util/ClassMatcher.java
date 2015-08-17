package de.codesourcery.toyprofiler.util;

import java.util.regex.Pattern;

public final class ClassMatcher
{
    private final Pattern pattern;
    private final boolean isWildcard;

    public ClassMatcher(String inputPattern)
    {
        String pattern = inputPattern.trim();
        if ( pattern.contains( "*" ) ) {
            isWildcard = true;
        } else {
            isWildcard = false;
        }
        while ( pattern.endsWith("." ) ) {
            pattern = pattern.substring(0, pattern.length()-1 );
        }
        pattern = pattern.replaceAll("\\.","/");

        if ( isWildcard ) {
            pattern = pattern.replace("*", ".*" );
        }
        this.pattern = Pattern.compile("^"+pattern,Pattern.CASE_INSENSITIVE);
    }

    public boolean matches(String className)
    {
        return pattern.matcher( className ).matches();
    }

    @Override
    public String toString() {
        return isWildcard ? pattern+" <wildcard>" : pattern.toString();
    }
}