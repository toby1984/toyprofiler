package de.codesourcery.toyprofiler.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.HeadlessException;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import javax.imageio.ImageIO;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileFilter;

import de.codesourcery.toyprofiler.MethodStatsHelper;
import de.codesourcery.toyprofiler.Profile;
import de.codesourcery.toyprofiler.Profile.MethodStats;
import de.codesourcery.toyprofiler.ui.FlameGraphRenderer.IDataProvider;
import de.codesourcery.toyprofiler.ui.FlameGraphRenderer.IVisitor;
import de.codesourcery.toyprofiler.util.IGridBagHelper;
import de.codesourcery.toyprofiler.util.IProfileIOAdapter;
import de.codesourcery.toyprofiler.util.XMLSerializer;

public class FlameGraphViewer extends JFrame implements IGridBagHelper
{
    protected static final DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssZZZ");
    protected static final DecimalFormat INVOCATION_COUNT_FORMAT = new DecimalFormat("###,###,###,###,##0");
    protected static final DecimalFormat DURATION_FORMAT = new DecimalFormat("#######0.0#####");
    protected static final DecimalFormat PERCENTAGE_FORMAT = new DecimalFormat("#####0.0#");
    
    private final JComboBox<Profile> profileSelector = new JComboBox<>();

    private final SelectionInfoPanel selectionInfoPanel = new SelectionInfoPanel();
    
    private File lastExportedImage;
    
    private final IProfileIOAdapter ioAdapter = new XMLSerializer();
    
    private final FlameGraphPanel graphPanel; 
    
    private final Preferences preferences = new Preferences();
    
    private final ViewingHistory history;
    
    private final HistoryDialog historyDialog = new HistoryDialog(preferences);

    public static final class MethodDataProvider implements IDataProvider<MethodStats> 
    {
        private final Profile currentProfile;
        private final MethodStatsHelper currentResolver;
        
        private final Profile previousProfile;
        private final MethodStatsHelper previousResolver;

        public MethodDataProvider(Profile currentProfile,MethodStatsHelper resolver) {
            this(currentProfile,null,null,resolver);
        }
        
        public MethodDataProvider(Profile currentProfile,Profile previousProfile,MethodStatsHelper previousResolver,MethodStatsHelper currentResolver) 
        {
            this.currentProfile = currentProfile;
            this.previousProfile = previousProfile;
            this.currentResolver = currentResolver;
            this.previousResolver = previousResolver;
        }

        public Profile getCurrentProfile() {
            return currentProfile;
        }

        @Override
        public MethodStats getRoot() {
            return currentProfile.getTopLevelMethod();
        }

        @Override
        public double getPercentageValue(MethodStats node) 
        {
            return node.getPercentageOfParentTime();
        }

        @Override
        public List<MethodStats> getChildren(MethodStats node) {
            return new ArrayList<>( node.getCallees().values() );
        }

        @Override
        public void visitSubtree(MethodStats startNode, IVisitor<MethodStats> visitor)
        {
            startNode.visit( (node,depth) -> visitor.visit( node , depth ) );
        }

        @Override
        public String getLabel(MethodStats node, Graphics2D graphics, int maxWidth)
        {
            final String clazz = currentResolver.getSimpleClassName( node );
            final String method = currentResolver.getMethodName( node );

            String className = clazz;
            String text = method;
            if ( getTextWidth( text , graphics ) < maxWidth )
            {
                for ( int i = 0 ; i <= clazz.length() ; i++ )
                {
                    className = clazz.substring(0,i);
                    String newText = (i!= clazz.length() ) ? className+"..."+method : className+"."+method;
                    if ( getTextWidth(newText,graphics) > maxWidth ) {
                        break;
                    }
                    text = newText;
                }
            }
            return text;
        }

        private int getTextWidth(String text,Graphics2D g)
        {
            final FontMetrics fm = g.getFontMetrics();
            return fm.stringWidth(text);
        }

        @Override
        public boolean isShowDifferences() 
        {
            return previousProfile != null;
        }

        @Override
        public double getPreviousPercentageValue(MethodStats currentNode) throws NoSuchElementException,IllegalStateException
        {
            if ( previousProfile == null ) {
                throw new IllegalStateException("Called without previous profile ?"); 
            }
            final int[] methodIds = currentNode.getPathFromRoot();
            final String[] methodNames = currentResolver.resolveMethodIds( methodIds );
            final int[] previousMethodIds = previousResolver.resolveMethodNames( methodNames );
            final MethodStats previousNode = previousProfile.lookupByPath( previousMethodIds );
            return previousNode.getPercentageOfParentTime();
        }
    }

    public static void main(String[] args) throws FileNotFoundException, IOException, HeadlessException, InvocationTargetException, InterruptedException
    {
        final FlameGraphViewer viewer = new FlameGraphViewer();
        if ( args.length > 0 ) {
            viewer.loadProfiles( new File[] { new File( args[0] ) } );
        }
    }

    private void updateTitle(Optional<ProfileData> current)
    {
        if ( current.isPresent() ) 
        {
            final String fileName = current.filter( ProfileData::hasFile ).flatMap( ProfileData::getSourceFile ).map( s -> " [ "+s.getAbsolutePath()+" ]").orElse( "" );
            
            final Optional<Profile> selectedProfile = current.flatMap( ProfileData::getSelectedProfile );
            
            final String profileName = selectedProfile.map( p-> p.getThreadName() ).orElse( "<no thread selected>" );
            final DateTimeFormatter formatter = DATE_PATTERN;
            final String timestamp = selectedProfile.flatMap( Profile::getCreationTime ).map( time -> formatter.format( time ) ).orElse( "<time unavailable>" );
            setTitle( profileName + fileName+" @ "+timestamp );
        } else {
            setTitle( "<no data loaded>" );
        }
    }
    
    public FlameGraphViewer() throws FileNotFoundException, IOException
    {
        setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        
        preferences.load();
        
        history = preferences.getHistory( ioAdapter );
        
        historyDialog.setHistory( history );
        
        Runtime.getRuntime().addShutdownHook( new Thread( () -> 
        {
            try 
            {
                System.out.println("Exiting...");
                preferences.setHistory( history );
                preferences.save();
            } 
            catch (Exception e) {
                e.printStackTrace();
            }
        }));

        profileSelector.setRenderer( new DefaultListCellRenderer() 
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,boolean cellHasFocus)
            {
                final Component result = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if ( value instanceof Profile )
                {
                    final Profile p = (Profile) value;
                    setText( p.getThreadName() );
                }
                return result;
            }
        });


        setJMenuBar( createMenuBar() );
        
        graphPanel = new FlameGraphPanel( preferences );
        graphPanel.addListener( selectionInfoPanel );
        
        history.addListener( graphPanel );
        history.addListener( (profile,triggeredFromComboBox) -> updateTitle(profile) );
        history.addListener( (profile,triggeredFromComboBox) -> 
        {
            if ( ! triggeredFromComboBox ) 
            {
                final List<Profile> profiles = profile.map( ProfileData::getProfiles ).orElse( Collections.emptyList() );
                profileSelector.setModel( new DefaultComboBoxModel<>( profiles.toArray( new Profile[ profiles.size() ] ) ) ); 
                final Profile selection = profile.flatMap( ProfileData::getSelectedProfile ).orElse( null );
                profileSelector.setSelectedItem( selection );
            }
        });
        history.addListener( historyDialog );

        profileSelector.addActionListener( ev ->
        {
            final Profile selectedItem = (Profile) profileSelector.getSelectedItem();
            System.out.println("User selected: "+selectedItem);
            if ( history.current().isPresent() ) {
                history.setCurrentProfile( selectedItem , true );
            }
        });
        
        final JPanel compound = new JPanel();
        compound.setLayout( new GridBagLayout() );

        // add label
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.weightx=0;
        cnstrs.weighty=0;
        cnstrs.gridx=0;
        cnstrs.gridy=0;
        cnstrs.fill = GridBagConstraints.NONE;
        compound.add( new JLabel("Thread:"), cnstrs );

        // add selection Box
        cnstrs = new GridBagConstraints();
        cnstrs.weightx=0;
        cnstrs.weighty=0;
        cnstrs.gridx=1;
        cnstrs.gridy=0;
        cnstrs.fill = GridBagConstraints.NONE;
        compound.add( profileSelector , cnstrs );

        // add selection info panel
        cnstrs = new GridBagConstraints();
        cnstrs.weightx=0;
        cnstrs.weighty=0;
        cnstrs.gridx=2;
        cnstrs.gridy=0;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        compound.add( selectionInfoPanel, cnstrs );		

        cnstrs = new GridBagConstraints();
        cnstrs.weightx=1;
        cnstrs.weighty=1;
        cnstrs.gridx=0;
        cnstrs.gridy=1;
        cnstrs.gridwidth=3;
        cnstrs.fill = GridBagConstraints.BOTH;
        compound.add( graphPanel , cnstrs );

        getContentPane().add( compound );
        setPreferredSize( new Dimension(640,480 ) );
        pack();
        setLocationRelativeTo( null );
        setVisible( true );
        
        history.historyChanged();
    }

    private JMenuBar createMenuBar() 
    {
        final JMenuBar result = new JMenuBar();

        // 'History' menu
        final JMenu historyMenu = new JMenu("History");
        addMenuItem("Previous", history::jumpToPrevious , historyMenu , key( KeyEvent.VK_LEFT) );
        addMenuItem("Next", history::jumpToNext , historyMenu , key( KeyEvent.VK_RIGHT ) );
        addMenuItem("History...", this::showHistory, historyMenu , key( KeyEvent.VK_H) );
        
        // 'Tools' menu
        final JMenu toolsMenu = new JMenu("Tools");
        addMenuItem("Export image...", this::exportImage , toolsMenu );


        // 'File' menu
        final JMenu fileMenu = new JMenu("File");

        addMenuItem("Load profiles...",()->  loadProfiles() , fileMenu );
        addMenuItem("Close current", this::closeCurrent, fileMenu , key( KeyEvent.VK_W, KeyEvent.CTRL_DOWN_MASK ) );
        
        addMenuItem("Reload", () -> 
        { 
            try {
                history.reloadCurrent();
            } catch (IOException e) {
                error("Failed to reload file",e);
            }
        }, fileMenu , key( KeyEvent.VK_F5 ) );
        
        addMenuItem("Preferences...", this::editPreferences, fileMenu );
        addMenuItem("Quit", () -> System.exit(0) , fileMenu );
        
        // register top-level menus
        result.add( fileMenu );
        result.add( historyMenu );
        result.add( toolsMenu );   
        return result;
    }
    
    private void editPreferences() {
        
        final JDialog dialog = new JDialog((Frame) null,"Preferences",true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        
        final PreferencesPanel panel = new PreferencesPanel( preferences) {
            
            @Override
            protected void onSave(Preferences preferences) 
            {
                try 
                {
                    preferences.save();
                } catch (IOException e) {
                    error("Saving preferences failed",e);
                } finally {
                    dialog.dispose();
                }
                preferences.notifyChange();
            }
            
            @Override
            protected void cancel() {
                dialog.dispose();
            }
        };
        
        dialog.add( panel );
        
        dialog.pack();
        dialog.setLocationRelativeTo( null );
        dialog.setVisible( true );
    }
    
    private void closeCurrent() {
        
        if ( history.current().isPresent() ) 
        {
            history.unload( Collections.singletonList( history.current().get() ) );
        }
    }
    
    private static KeyStroke key(int keyCode,int modifiers) {
        return KeyStroke.getKeyStroke( keyCode , modifiers );
    }
    
    private static KeyStroke key(int keyCode) {
        return key(keyCode,0);
    }
    
    private JMenuItem addMenuItem(String label,Runnable action,JMenu menu) {
        return addMenuItem(label,action,menu,null);
    }
    
    private JMenuItem addMenuItem(String label,Runnable action,JMenu menu,KeyStroke shortcut) {
        final JMenuItem item = new JMenuItem( label );
        item.addActionListener( ev -> 
        {
            action.run();
        });
        if ( shortcut != null ) {
            item.setAccelerator( shortcut );
        }
        menu.add( item );
        return item;
    }
    
    private void showHistory() 
    {
        historyDialog.setVisible( true );
    }
    
    private void loadProfiles() 
    {
        final JFileChooser chooser = new JFileChooser( );
        final Optional<File> latestFile = preferences.getLastLoadDirectory();
        if ( latestFile.isPresent() ) 
        {
            chooser.setCurrentDirectory( latestFile.get() );
        }
        
        chooser.setFileHidingEnabled(true);
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter( new FileFilter() 
        {
            @Override
            public String getDescription() {
                return "XML profile files";
            }
            
            @Override
            public boolean accept(File f) 
            {
                return f.isDirectory() || ( f.isFile() && f.getName().endsWith(".xml") );
            }
        });
        
        if ( chooser.showOpenDialog( null ) == JFileChooser.APPROVE_OPTION && chooser.getSelectedFiles().length >= 1 ) 
        {
            preferences.setLastLoadDirectory( chooser.getSelectedFiles()[0].getParentFile() );
            loadProfiles( chooser.getSelectedFiles() );
        }
    }
    
    private void loadProfiles(File[] files) 
    {
        for ( File file : files ) 
        {
            try ( FileInputStream in =new FileInputStream(file) )
            {
                history.add( file , ioAdapter.load( in ) );
            } 
            catch(Exception e) 
            {
                error("Failed to load data from "+file.getAbsolutePath()+" ("+e.getMessage()+")",e);
            }
        }
    }
    
    private void exportImage() 
    {
        if ( history.size() == 0 ) {
            error("No data to render");
            return;
        }
        
        final JDialog dialog = new JDialog();
        dialog.setDefaultCloseOperation( JDialog.DISPOSE_ON_CLOSE );

        dialog.setLayout( new GridLayout( 4 , 2 ) );

        dialog.add( new JLabel("Width (pixels):" ) );
        final JTextField width = new JTextField( Integer.toString( graphPanel.getWidth() ) );
        width.setColumns( 4 );
        dialog.add( width );

        dialog.add( new JLabel("Height (pixels):" ) );
        final JTextField height = new JTextField( Integer.toString( graphPanel.getHeight() ));
        height.setColumns( 4 );
        dialog.add( height );

        dialog.add( new JLabel("Output file:") );

        final JPanel panel = new JPanel();
        panel.setLayout( new FlowLayout() );
        final JTextField outputFile = new JTextField("");

        if ( lastExportedImage != null ) {
            outputFile.setText( lastExportedImage.getAbsolutePath() );
        }
        outputFile.setEditable(false);
        outputFile.setColumns( 20 );

        final JButton button = new JButton("Choose");
        button.addActionListener( ev -> 
        {
            final JFileChooser chooser = new JFileChooser();
            if ( lastExportedImage != null ) {
                chooser.setSelectedFile( lastExportedImage );
            }
            final int result = chooser.showSaveDialog( dialog );
            if ( result == JFileChooser.APPROVE_OPTION ) 
            {
                lastExportedImage = chooser.getSelectedFile();
                outputFile.setText( lastExportedImage.getAbsolutePath() );
            }
            chooser.setVisible( false );
        });

        panel.add( outputFile );
        panel.add( button );
        dialog.add( panel );

        final JButton cancel = new JButton("Cancel");
        cancel.addActionListener( ev -> 
        {
            dialog.setVisible(false);
        });

        final JButton export = new JButton("Export");
        export.addActionListener( ev -> 
        {
            if ( outputFile.getText() == null || outputFile.getText().trim().length() == 0 ) 
            {
                error("You need to select a file");
                return;
            }

            final int w;
            final int h;
            try {
                w = Integer.parseInt( width.getText() == null ? null : width.getText().trim() );
                h = Integer.parseInt( height.getText() == null ? null : height.getText().trim()  );
            }
            catch(Exception e) {
                error("You need to enter valid height/width values",e);
                return;
            }

            if ( w < 1 || h < 1 ) {
                error("Width and heigth need to be >= 1");
                return;
            }

            try {
                final BufferedImage image = graphPanel.renderCustomImage( w , h );
                if ( ! ImageIO.write( image ,"png" , lastExportedImage ) ) {
                    throw new RuntimeException("No PNG writer available?");
                }
            } 
            catch (Exception e) {
                error("Failed to export file: "+e.getMessage(),e);
                return;
            }

            info("Saved "+w+" x "+h+" image to "+outputFile.getText() );
            dialog.setVisible( false );
        });

        dialog.add( cancel );
        dialog.add( export );

        dialog.setModal( true );
        dialog.setTitle("Export image");

        dialog.pack();
        dialog.setLocationRelativeTo( null );
        dialog.setVisible( true );
    }
    
    protected interface ThrowingRunnable 
    {
        public void run() throws Exception;
    }
    
    protected static String millisToString(double millis) 
    {
        if ( millis < 0.1 ) 
        {
            double nanos = millis* 1_000_000;
            return DURATION_FORMAT.format( nanos )+" ns";
        } 
        if ( millis < 10000 ) {
            return DURATION_FORMAT.format( millis )+" ms";
        }
        double seconds = millis / 1000;
        return DURATION_FORMAT.format(seconds)+" s";
    }    
}
