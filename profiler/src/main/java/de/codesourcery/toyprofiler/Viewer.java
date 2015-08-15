package de.codesourcery.toyprofiler;

import java.awt.HeadlessException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import de.codesourcery.toyprofiler.ui.FlameGraphViewer;
import de.codesourcery.toyprofiler.ui.TreeTableViewer;

public class Viewer 
{
    public static void main(String[] args) throws HeadlessException, FileNotFoundException, InvocationTargetException, IOException, InterruptedException 
    {
        if ( Arrays.stream(args).anyMatch( arg -> arg.equals("-s" ) ) ) 
        {
            TreeTableViewer.main( args );
        } else {
            FlameGraphViewer.main( args );
        }
    }
}
