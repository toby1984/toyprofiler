package de.codesourcery.toyprofiler.ui;

import java.awt.Dimension;
import java.awt.GridBagLayout;

import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;

import de.codesourcery.toyprofiler.util.ConstraintBuilder;
import de.codesourcery.toyprofiler.util.IGridBagHelper;

public abstract class ColorSchemeEditorPanel extends JPanel implements IGridBagHelper
{
    private final ColorScheme original;
    private final ColorScheme toEdit;
    
    private final JTextField schemeName; 
    
    public ColorSchemeEditorPanel(ColorScheme scheme) 
    {
        setPreferredSize( new Dimension(400,400 ) );
        
        this.original = scheme;
        this.toEdit = scheme.createCopy();
        
        setLayout( new GridBagLayout() );
        
        // name
        label( "Name" , cnstrs(0,0) );
        schemeName = textField( toEdit::getName , toEdit::setName , cnstrs(1,0) );
        schemeName.setColumns( 15 );
        
        // end color
        label( "Start color" , cnstrs(0,1) );
        colorChooser( toEdit::getStart , toEdit::setStart , cnstrs(1,1) );
        
        // start color
        label( "End color" , cnstrs(0,2) );
        colorChooser( toEdit::getEnd , toEdit::setEnd , cnstrs(1,2) );
        
        // selection color
        label( "Selection color" , cnstrs(0,3) );
        colorChooser( toEdit::getSelectionColor, toEdit::setSelectionColor, cnstrs(1,3) );        

        // color count
        label("Color count" , cnstrs(0,4) );
        final JSlider slider = new JSlider( JSlider.HORIZONTAL ,  2 , 16 , toEdit.getColorCount() );
        slider.setPaintTicks(true);
        slider.setMajorTickSpacing(2);
        slider.setMinorTickSpacing( 1 );
        slider.setSnapToTicks(true);
        
        final boolean[] listenerEnabled = { true };
        
        final JTextField colorCount = new JTextField( Integer.toString( toEdit.getColorCount() ) );
        colorCount.setColumns( 3 );
        
        colorCount.addActionListener( ev -> 
        {
            if ( listenerEnabled[0] ) 
            {
                final int count = Integer.parseInt( colorCount.getText() );
                toEdit.setColorCount( count );
                slider.setValue( count );
            }
        });
        
        slider.getModel().addChangeListener( event -> 
        {
            toEdit.setColorCount( slider.getValue() );
            listenerEnabled[0] = false;
            colorCount.setText( Integer.toString( slider.getValue() ) );
            listenerEnabled[0] = true;
        });        
        
        final JPanel compound = new JPanel();
        compound.setLayout( new GridBagLayout() );
        compound.add( slider , cnstrs(0,0).weightX(1).build() );
        compound.add( colorCount , cnstrs(0,1).build()  );
        
        add( compound ,cnstrs( 1 ,4 ) );
        
        // good difference
        label( "'Good difference' color" , cnstrs(0,5) );
        colorChooser( toEdit::getGoodDifferenceColor , toEdit::setGoodDifferenceColor , cnstrs(1,5) );
        
        // bad difference
        label( "'Bad difference' color" , cnstrs(0,6) );
        colorChooser( toEdit::getBadDifferenceColor , toEdit::setBadDifferenceColor , cnstrs(1,6) );
        
        
        button("Cancel" , this::cancel , cnstrs(0,7) );
        button("Save" , this::save , cnstrs(1,7) );
    }
    
    private ConstraintBuilder cnstrs(int x,int y) {
        return new ConstraintBuilder(x,y);
    }
    
    protected abstract void cancel();
    
    private void save() 
    {
        toEdit.setName( schemeName.getText() );
        toEdit.setup();
        onSave( toEdit , original );
    }
    
    protected abstract void onSave(ColorScheme edited,ColorScheme original);
}