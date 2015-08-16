package de.codesourcery.toyprofiler.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagLayout;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;
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
    
    private ColorScheme selectedDefaultScheme;
    private ColorScheme selectedDefaultCompareScheme;
    
    private final MyComboModel defaultSchemeModel = new MyComboModel( () -> selectedDefaultScheme , value -> selectedDefaultScheme = value );
    private final MyComboModel compareSchemeModel = new MyComboModel( () -> selectedDefaultCompareScheme , value -> selectedDefaultCompareScheme = value );
    
    private final JComboBox<ColorScheme> defaultScheme;
    private final JComboBox<ColorScheme> compareScheme;
    
    protected final class MyComboModel extends AbstractListModel<ColorScheme> implements ComboBoxModel<ColorScheme>{
        
        private final Supplier<ColorScheme> getter;
        private final Consumer<ColorScheme> setter;
        
        public MyComboModel(Supplier<ColorScheme> getter,Consumer<ColorScheme> setter) {
            this.setter = setter;
            this.getter = getter;
        }
        public void modelChanged() 
        {
            fireContentsChanged( this , 0 , colorSchemes.size() );
        }

        @Override
        public int getSize() {
            return colorSchemes.size();
        }

        @Override
        public ColorScheme getElementAt(int index) {
            return colorSchemes.get(index);
        }

        @Override
        public void setSelectedItem(Object anItem) {
            setter.accept( (ColorScheme) anItem ); 
        }

        @Override
        public ColorScheme getSelectedItem() {
            return getter.get();
        }
    }
    
    public PreferencesPanel(Preferences preferences) 
    {
        setPreferredSize( new Dimension(600,400 ) );
        
        this.original = preferences;
        this.toEdit = preferences.createCopy();
        
        setLayout( new GridBagLayout() );
        
        // get color schemes
        colorSchemes = preferences.getColorSchemes();
        
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
        defaultScheme = new JComboBox<ColorScheme>( defaultSchemeModel );
        defaultScheme.setSelectedItem( preferences.getDefaultColorScheme()  );
        defaultScheme.setRenderer( listRenderer );
        
        label( "Default color scheme" , cnstrs( 0 , 0 ).anchorLeft() );
        add( defaultScheme , cnstrs( 1 , 0 ).build() );
        button( "Edit" , () -> editColorScheme( (ColorScheme) defaultScheme.getSelectedItem() , scheme -> 
        {
            defaultSchemeModel.setSelectedItem( scheme );
            preferences.setDefaultColorScheme(scheme);
            updateComboBoxModels();
        }) ,  cnstrs(2,0 ) );
        
        // combo box to select default comparison color scheme
        compareScheme = new JComboBox<ColorScheme>( compareSchemeModel );
        compareScheme.setSelectedItem( preferences.getDefaultCompareColorScheme() );
        compareScheme.setRenderer( listRenderer );
        
        label( "Comparison color scheme" , cnstrs( 0 , 1 ).anchorLeft() );
        add( compareScheme , cnstrs( 1 , 1 ).build() );
        button( "Edit" , () -> editColorScheme( (ColorScheme) compareScheme.getSelectedItem() , scheme -> 
        {
            compareSchemeModel.setSelectedItem( scheme );
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
        
        defaultSchemeModel.modelChanged();
        compareSchemeModel.modelChanged();
    }
    
    private ConstraintBuilder cnstrs(int x,int y) {
        return new ConstraintBuilder(x,y);
    }
    
    private void editColorScheme(ColorScheme scheme,Consumer<ColorScheme> saveAction) {
        
        final JDialog dialog = new JDialog((Frame) null , "Edit color scheme: "+scheme.getName() , true );
        
        dialog.add( new ColorSchemeEditorPanel( scheme ) 
        {
            @Override
            protected void onSave(ColorScheme edited,ColorScheme original) 
            {
                if ( colorSchemes.stream().filter( s -> s != original ).anyMatch( s -> s.getName().equals( edited.getName() ) ) ) 
                {
                    error("Color scheme name '"+edited.getName()+"' is already in use");
                    return;
                }
                original.populateFrom( edited );
                
                toEdit.setColorSchemes( colorSchemes );
                
                toEdit.setDefaultColorScheme( defaultSchemeModel.getSelectedItem() );
                toEdit.setDefaultCompareColorScheme( compareSchemeModel.getSelectedItem() );

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
