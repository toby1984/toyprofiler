package de.codesourcery.toyprofiler;

final class ClassMatcher
{
    private final String pattern;
    private final boolean isWildcard;

    public ClassMatcher(String inputPattern)
    {
        String pattern = inputPattern.trim();
        if ( pattern.endsWith("*" ) ) {
            isWildcard = true;
            pattern = pattern.substring(0, pattern.length()-1 );
        } else {
            isWildcard = false;
        }
        while ( pattern.endsWith("." ) ) {
            pattern = pattern.substring(0, pattern.length()-1 );
        }
        this.pattern = pattern.replaceAll("\\.","/");
        if ( ! Agent.VALID_CLASSNAME.matcher( this.pattern ).matches() ) {
            throw new IllegalArgumentException("Invalid class name pattern: '"+inputPattern+"'");
        }
    }

    public boolean matches(String className)
    {
        if ( isWildcard ) {
            return className.startsWith( pattern );
        }
        return className.equals( pattern );
    }

    @Override
    public String toString() {
        return isWildcard ? pattern+" <wildcard>" : pattern;
    }
}