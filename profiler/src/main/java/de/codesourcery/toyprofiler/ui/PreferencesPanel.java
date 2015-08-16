package de.codesourcery.toyprofiler.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagLayout;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;

import de.codesourcery.toyprofiler.util.ConstraintBuilder;
import de.codesourcery.toyprofiler.util.IGridBagHelper;

public abstract class PreferencesPanel extends JPanel implements IGridBagHelper {

    private final Preferences toEdit;
    private final Preferences original;
    
    private final List<ColorScheme> colorSchemes;
    
    private final JComboBox<ColorScheme> defaultScheme;
    private final JComboBox<ColorScheme> compareScheme;
    
    public PreferencesPanel(Preferences preferences) 
    {
        setPreferredSize( new Dimension(600,400 ) );
        
        this.original = preferences;
        this.toEdit = preferences.createCopy();
        
        setLayout( new GridBagLayout() );
        
        // get color schemes
        colorSchemes = preferences.getColorSchemes();
        
        // get default schemes (add if they're missing)
        final ColorScheme defaultSelected = preferences.getDefaultColorScheme();
        if ( ! colorSchemes.contains( defaultSelected ) ) {
            colorSchemes.add( defaultSelected );
        }
        
        final ColorScheme defaultCmpSelected = preferences.getDefaultCompareColorScheme();
        if ( ! colorSchemes.contains( defaultCmpSelected ) ) {
            colorSchemes.add( defaultCmpSelected );
        }
        
        // sort ascending by scheme name
        colorSchemes.sort( Comparator.comparing( ColorScheme::getName ));
        
        final DefaultListCellRenderer listRenderer = new DefaultListCellRenderer() 
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) 
            {
                final Component result = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if ( value instanceof ColorScheme) {
                    setText( ((ColorScheme) value).getName() );
                }
                return result;
            }
        };
        
        // combo box to select default color scheme
        defaultScheme = new JComboBox<>( new DefaultComboBoxModel<>( colorSchemes.toArray( new ColorScheme[ colorSchemes.size() ] ) ) );
        defaultScheme.setSelectedItem( defaultSelected );
        defaultScheme.setRenderer( listRenderer );
        
        label( "Default color scheme" , cnstrs( 0 , 0 ).anchorLeft() );
        add( defaultScheme , cnstrs( 1 , 0 ).build() );
        button( "Edit" , () -> editColorScheme( (ColorScheme) defaultScheme.getSelectedItem() , scheme -> 
        {
            preferences.setDefaultColorScheme(scheme);
            updateComboBoxModels();
        }) ,  cnstrs(2,0 ) );
        
        // combo box to select default comparison color scheme
        compareScheme = new JComboBox<>( new DefaultComboBoxModel<>( colorSchemes.toArray( new ColorScheme[ colorSchemes.size() ] ) ) );
        compareScheme.setSelectedItem( defaultCmpSelected );
        compareScheme.setRenderer( listRenderer );
        
        label( "Comparison color scheme" , cnstrs( 0 , 1 ).anchorLeft() );
        add( compareScheme , cnstrs( 1 , 1 ).build() );
        button( "Edit" , () -> editColorScheme( (ColorScheme) compareScheme.getSelectedItem() , scheme -> 
        {
            preferences.setDefaultCompareColorScheme(scheme);
            updateComboBoxModels();
        }) ,  cnstrs(2,1 ) );
        
        // buttons
        button("Cancel" , this::cancel , cnstrs( 0 , 2 ) );
        button("Save" , this::save , cnstrs( 1 , 2 ).width(2) );
    }
    
    private void updateComboBoxModels() 
    {
        // sort ascending by scheme name
        colorSchemes.sort( Comparator.comparing( ColorScheme::getName ));
        
        final DefaultComboBoxModel<ColorScheme> model1 = new DefaultComboBoxModel<>( colorSchemes.toArray( new ColorScheme[ colorSchemes.size() ] ) );
        final DefaultComboBoxModel<ColorScheme> model2 = new DefaultComboBoxModel<>( colorSchemes.toArray( new ColorScheme[ colorSchemes.size() ] ) );
        
        defaultScheme.setModel( model1 );
        compareScheme.setModel( model2 );
        
        // get default schemes (add if they're missing)
        final ColorScheme defaultSelected = toEdit.getDefaultColorScheme();
        defaultScheme.setSelectedItem( defaultSelected );
        
        final ColorScheme defaultCmpSelected = toEdit.getDefaultCompareColorScheme();
        compareScheme.setSelectedItem( defaultCmpSelected );
    }
    
    
    private ConstraintBuilder cnstrs(int x,int y) {
        return new ConstraintBuilder(x,y);
    }
    
    private void editColorScheme(ColorScheme scheme,Consumer<ColorScheme> saveAction) {
        
        final JDialog dialog = new JDialog((Frame) null , "Edit color scheme: "+scheme.getName() , true );
        
        dialog.add( new ColorSchemeEditorPanel( scheme ) 
        {
            @Override
            protected void onSave(ColorScheme scheme) 
            {
                toEdit.setColorSchemes( colorSchemes );
                dialog.dispose();
                saveAction.accept( scheme );
            }
            
            @Override
            protected void cancel() {
                dialog.dispose();
            }
        });
        
        dialog.pack();
        dialog.setLocationRelativeTo( null );
        dialog.setVisible( true );
    }
    
    protected abstract void cancel();
    
    private void save() {
        this.original.populateFrom( toEdit );
        this.original.setColorSchemes( colorSchemes );
        onSave( this.original );
    }
    
    protected abstract void onSave(Preferences preferences);
}
