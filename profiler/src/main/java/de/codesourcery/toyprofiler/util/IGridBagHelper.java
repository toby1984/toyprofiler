package de.codesourcery.toyprofiler.util;

import java.awt.Color;
import java.awt.Component;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

public interface IGridBagHelper 
{
    public default JLabel label(String s , ConstraintBuilder builder) {
        final JLabel label = new JLabel(s);
        add( label , builder.build() );
        return label;
    }
    
    public default JButton button(String label,Runnable action,ConstraintBuilder builder) 
    {
        final JButton button = new JButton(label);
        button.addActionListener( ev -> action.run() );
        add( button , builder.build() );
        return button;
    }    
    
    public default JTextField textField(Supplier<String> toEdit , Consumer<String> saveAction,ConstraintBuilder builder) 
    {
        final JTextField tf = new JTextField();
        tf.setText( toEdit.get() );
        tf.addActionListener( ev -> 
        {
            saveAction.accept( tf.getText() );
        });
        
        add( tf , builder.build() );
        return tf;
    }
    
    public default void colorChooser(Supplier<Color> toEdit , Consumer<Color> saveAction,ConstraintBuilder builder) 
    {
        final JButton button = new JButton("Edit");
        button.setBackground( toEdit.get() );
        
        add( button , builder.build() );
        
        final Runnable action = () -> 
        {
            final Color result = JColorChooser.showDialog( null , "Pick color" , toEdit.get() );
            if ( result != null ) 
            {
                button.setBackground( result );
                saveAction.accept( result );
            }
        };
        button.addActionListener( ev -> action.run() );
    }
    
    public default void add(Component component,ConstraintBuilder constraints) {
        add( component , constraints.build());
    }
    
    public void add(Component component,Object constraints);
    
    public default void error(String message) 
    {
        error(message,null);
    }
    
    public default void error(String message,Throwable t) 
    {
        if ( t != null ) {
            t.printStackTrace();
        }
        JOptionPane.showMessageDialog(null, message , "Error", JOptionPane.ERROR_MESSAGE );
    }

    public default void info(String message) {
        JOptionPane.showMessageDialog(null, message , "Information", JOptionPane.INFORMATION_MESSAGE );
    }
}
