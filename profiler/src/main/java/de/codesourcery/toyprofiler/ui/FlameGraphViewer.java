package de.codesourcery.toyprofiler.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.HeadlessException;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.table.DefaultTableModel;

import de.codesourcery.toyprofiler.IRawMethodNameProvider;
import de.codesourcery.toyprofiler.MethodStatsHelper;
import de.codesourcery.toyprofiler.Profile;
import de.codesourcery.toyprofiler.Profile.MethodStats;
import de.codesourcery.toyprofiler.ui.FlameGraphRenderer.FlameGraph;
import de.codesourcery.toyprofiler.ui.FlameGraphRenderer.IDataProvider;
import de.codesourcery.toyprofiler.ui.FlameGraphRenderer.IVisitor;
import de.codesourcery.toyprofiler.ui.FlameGraphRenderer.RectangularRegion;
import de.codesourcery.toyprofiler.ui.ViewingHistory.IViewChangeListener;
import de.codesourcery.toyprofiler.util.XMLSerializer;

public class FlameGraphViewer extends JFrame
{
    protected static final DateTimeFormatter DATE_PATTERN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssZZZ");
    protected static final DecimalFormat INVOCATION_COUNT_FORMAT = new DecimalFormat("###,###,###,###,##0");
    protected static final DecimalFormat DURATION_FORMAT = new DecimalFormat("#######0.0#####");
    
    private final JComboBox<Profile> profileSelector = new JComboBox<>();

    private final SelectionInfoPanel selectionInfoPanel = new SelectionInfoPanel();
    
    private File lastExportedImage;
    
    private final FlameGraphPanel graphPanel; 
    
    private final ViewingHistory history = new ViewingHistory();
    private final HistoryDialog historyDialog = new HistoryDialog();

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
        public double getPreviousPercentageValue(MethodStats currentNode) 
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

    protected final class SelectionInfoPanel extends JPanel 
    {
        private final JTextField className = new JTextField();
        private final JTextField methodName = new JTextField();
        private final JTextField invocations = new JTextField();
        private final JTextField totalTime = new JTextField();
        private final JTextField ownTime = new JTextField();

        public SelectionInfoPanel() 
        {
            setLayout( new GridBagLayout() );
            label( "Class:" , 0 );       
            textfield( className ,1 , 0.3 );   // 0.3

            label( "Method:" , 2 );      
            textfield( methodName , 3 , 0.3 ); // 0.3

            label( "Invocations:" , 4 ); 
            textfield( invocations ,5 , 0.1333 );  // 0.13

            label( "Total time:" ,6  );   
            textfield( totalTime ,7 , 0.1333 );    // 0.13
            
            label( "Own time:" ,8  );   
            textfield( ownTime ,9 , 0.1333 );    // 0.13          
        }

        private GridBagConstraints cnstrs(int x,double weightX) 
        {
            final GridBagConstraints cnstrs = new GridBagConstraints();
            cnstrs.weightx=weightX;
            cnstrs.weighty=0;
            cnstrs.gridx=x;
            cnstrs.gridy=0;
            cnstrs.fill = GridBagConstraints.HORIZONTAL;
            return cnstrs;
        }

        private GridBagConstraints labelCnstrs(int x) 
        {
            final GridBagConstraints cnstrs = new GridBagConstraints();
            cnstrs.weightx=0;
            cnstrs.weighty=0;
            cnstrs.gridx=x;
            cnstrs.gridy=0;
            cnstrs.fill = GridBagConstraints.NONE;
            return cnstrs;
        }

        private void textfield(JTextField tf,int x,double weightX) {
            tf.setEditable(false);
            add( tf ,cnstrs( x, weightX ) );
        }

        private void label(String text,int x) {
            add( new JLabel( text) , labelCnstrs(x) );
        }
        public void setSelection(MethodStats s,MethodStatsHelper resolver) 
        {
            className.setText( s == null ? "" : resolver.getClassName(s) );
            methodName.setText( s == null ? "" : resolver.getMethodName(s)+"("+resolver.getMethodSignature(s)+")" );
            invocations.setText( s == null ? "" : INVOCATION_COUNT_FORMAT.format( s.getInvocationCount() ) );
            totalTime.setText( s == null ? "" : millisToString( s.getTotalTimeMillis() ) );
            ownTime.setText( s == null ? "" : millisToString( s.getOwnTimeMillis() ) );
        }
    }

    protected final class FlameGraphPanel extends JPanel implements IViewChangeListener
    {
        private FlameGraphRenderer<MethodStats> renderer;

        private int w = -1;
        private int h = -1;
        
        private MethodStatsHelper resolver;
        private MethodDataProvider dataProvider;
        
        private FlameGraph<MethodStats> graph;

        private RectangularRegion<MethodStats> highlight;
        
        private RectangularRegion<MethodStats> currentSelection;
        private MethodStats zoom;

        private final MouseAdapter mouseListener = new MouseAdapter()
        {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e)
            {
                if ( graph != null )
                {
                    final RectangularRegion<MethodStats> region = graph.getRegion( e.getX() ,  e.getY() );
                    setToolTipText( region == null ? null : getToolTip( region.stats ) );
                    if ( ( highlight == null || ! highlight.matches( region ) ) ) 
                    {
                        highlight = region;
                        if ( currentSelection == null ) {
                            selectionInfoPanel.setSelection( highlight == null ? null : highlight.stats , resolver );
                        }
                        repaint();
                    }
                }
            }

            private String getToolTip( MethodStats stats)
            {
                final StringBuilder buffer = new StringBuilder("<HTML><BODY>");
                final Map<String,String> data = new LinkedHashMap<>();
                data.put("Class", resolver.getRawClassName(stats) );
                data.put("Method", resolver.getMethodName(stats) );
                data.put("Signature", resolver.getRawMethodSignature(stats));
                data.put("Invocations" , INVOCATION_COUNT_FORMAT.format( stats.getInvocationCount() ) );
                data.put("Total time" , millisToString( stats.getTotalTimeMillis() ) );
                data.put("Own time" , millisToString( stats.getOwnTimeMillis() ) );

                final int col0MaxLen = data.keySet().stream().mapToInt( s -> s.length() ).max().orElse(0);
                for (final Iterator<String> it = new ArrayList<>( data.keySet() ).iterator(); it.hasNext();)
                {
                    final String origKey = it.next();
                    String key = origKey;
                    while ( key.length() < col0MaxLen ) {
                        key += " ";
                    }
                    buffer.append("<B>"+key+":</B> "+data.get(origKey) );
                    if ( it.hasNext() ) {
                        buffer.append("<BR/>");
                    }
                }

                buffer.append("</BODY></HTML>");
                return buffer.toString();
            }

            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                boolean zoomChanged = false;
                if ( e.getButton() == MouseEvent.BUTTON3 && zoom != null ) { // zoom out
                    final RectangularRegion<MethodStats> first = graph.getFirst();
                    zoom = first == null ? null : first.stats.getParent();
                    zoomChanged=true;
                }
                else if ( e.getButton() == MouseEvent.BUTTON1 ) // zoom in
                {
                    final RectangularRegion<MethodStats> region = graph.getRegion( e.getX() ,  e.getY() );
                    if ( e.getClickCount() == 2 ) 
                    {
                        if ( region != null ) {
                            zoom = region.stats;
                            zoomChanged=true;
                        }
                    } 
                    else 
                    {
                        if ( region != null && ( currentSelection == null || ! currentSelection.matches( region ) ) ) 
                        {
                            currentSelection = region;
                            selectionInfoPanel.setSelection( currentSelection == null ? null : currentSelection.stats , resolver );
                            repaint();
                        } else {
                            currentSelection = null;
                            selectionInfoPanel.setSelection( null , resolver );
                            repaint();
                        }
                    }
                }

                if ( zoomChanged )
                {
                    currentSelection = null;
                    forcedRepaint();
                }
            }
        };
        
        public void viewChanged(java.util.Optional<ProfileData> data,boolean triggeredFromComboBox) 
        {
            if ( data.isPresent() ) 
            {
                resolver = new MethodStatsHelper( data.get() );
                dataProvider = new MethodDataProvider( data.get().getSelectedProfile().get() , resolver );
                
            } else {
                dataProvider = null;
                resolver = MethodStatsHelper.NOP_INSTANCE;
            }
            renderer = new FlameGraphRenderer<MethodStats>( dataProvider );
            forcedRepaint();
        }

        public FlameGraphPanel()
        {
            addMouseListener( mouseListener );
            addMouseMotionListener( mouseListener);
        }

        public void forcedRepaint() {
            graph = null;
            repaint();
        }

        public BufferedImage renderCustomImage(int w,int h) 
        {
            return renderGraph( w , h ).getImage();
        }

        public FlameGraph<MethodStats> renderGraph(int w,int h) 
        {
            if ( zoom != null ) {
                return renderer.render( zoom , w  , h );
            } 
            return renderer.render( w  , h );
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            if ( dataProvider == null ) 
            {
                super.paintComponent(g);
                return;
            }
            
            if ( graph == null || getWidth() != w || getHeight() != h )
            {
                w = getWidth();
                h = getHeight();

                graph = renderGraph(w,h);
                if ( currentSelection != null ) {
                    currentSelection = graph.find( currentSelection.stats );
                    selectionInfoPanel.setSelection( currentSelection == null ? null : currentSelection.stats  , resolver );
                }
                if ( highlight != null ) {
                    highlight = graph.find( highlight.stats );
                }
            }
            g.drawImage( graph.getImage() , 0 , 0 , null );

            final RectangularRegion<MethodStats> toHighlight = currentSelection != null ? currentSelection : highlight;
            if ( toHighlight != null )
            {
                g.setColor( Color.BLUE );
                ((Graphics2D) g).draw( toHighlight );
            }
        }
    }

    public static void main(String[] args) throws FileNotFoundException, IOException, HeadlessException, InvocationTargetException, InterruptedException
    {
        if ( args.length != 1 ) {
            throw new RuntimeException("Expected exactly one argument (XML file to load)");
        }
        
        final FlameGraphViewer viewer = new FlameGraphViewer();
        viewer.loadProfiles( new File( args[0] ) );
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
        
        graphPanel = new FlameGraphPanel();
        
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
            history.setCurrentProfile( (Profile) profileSelector.getSelectedItem() , true );
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
        compound.add( new JLabel("Profile:"), cnstrs );

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
        setVisible( true );
    }

    private JMenuBar createMenuBar() 
    {
        final JMenuBar result = new JMenuBar();

        final JMenu fileMenu = new JMenu("File");
        result.add( fileMenu );

        addMenuItem("Load profiles...",()->  loadProfiles() , fileMenu );
        addMenuItem("History...", this::showHistory, fileMenu , key( KeyEvent.VK_H) );
        addMenuItem("Reload", history::reloadCurrent , fileMenu , key( KeyEvent.VK_F5 ) );
        addMenuItem("Previous", history::previous , fileMenu , key( KeyEvent.VK_LEFT) );
        addMenuItem("Next", history::next , fileMenu , key( KeyEvent.VK_RIGHT ) );
        addMenuItem("Export image...", this::exportImage , fileMenu );
        
        return result;
    }
    
    private static KeyStroke key(int keyCode) {
        return KeyStroke.getKeyStroke( keyCode , 0 );
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
    
    protected final class HistoryTableModel extends DefaultTableModel implements IViewChangeListener
    {
        private final List<ProfileData> data = new ArrayList<>();
        
        public String getColumnName(int column) 
        {
            switch(column) 
            {
                case 0: return "Active";
                case 1: return "File";
                case 2: return "Timestamp";
                case 3: return "Description";
                default:
                    throw new IllegalArgumentException();
            }
        }
        
        @Override
        public int getRowCount() {
            return data != null ? data.size() : 0;
        }
        
        public int getColumnCount() {
            return 4;
        }
        
        public java.lang.Class<?> getColumnClass(int column) {
            switch(column) 
            {
                case 0: return Boolean.class;
                case 1: 
                case 2: 
                case 3: 
                    return String.class;
                default:
                    throw new IllegalArgumentException();
            }
        }
        
        public ProfileData getRow(int row) {
            return data.get(row);
        }
        
        public Object getValueAt(int row, int column) 
        {
            final ProfileData item = getRow(row);
            switch( column ) 
            {
                case 0:
                    return history.current().isPresent() && history.current().get() == item;
                case 1:
                    return item.getSourceFile().map( f -> f.getAbsolutePath() ).orElse("<no file>");
                case 2:
                    return item.getTimestamp().map( DATE_PATTERN::format ).orElse( "<no timestamp>");
                case 3:
                    return item.getDescription().orElse("");
               default:
                   throw new IllegalArgumentException();
            }
        }
        
        @Override
        public boolean isCellEditable(int row, int column) 
        {
            switch(column) {
                case 0:
                case 1:
                case 2: 
                    return false;
                case 3:
                    return getRow(row).getSelectedProfile().isPresent();
                default:
                    throw new IllegalArgumentException();
            }
        }
        
        @Override
        public void setValueAt(Object aValue, int row, int column) 
        {
            if ( column != 3 || !(aValue instanceof String) ) {
                throw new IllegalArgumentException(); 
            }
            getRow(row).setDescription( (String) aValue );
        }

        @Override
        public void viewChanged(Optional<ProfileData> data,boolean triggeredFromComboBox) 
        {
            this.data.clear();
            this.data.addAll( history.getItems() );
            fireTableDataChanged();
        }
    }
    
    protected class HistoryDialog extends JDialog implements IViewChangeListener
    {
        private final HistoryTableModel tableModel = new HistoryTableModel();
        private final JTable table = new JTable( tableModel);
        
        public HistoryDialog() 
        {
            setDefaultCloseOperation( JDialog.HIDE_ON_CLOSE );

            final JPanel panel = new JPanel();
            final JButton closeDialog = new JButton("Close");
            closeDialog.addActionListener( ev -> 
            {
                setVisible(false);
            });
            
            final JButton compare = new JButton("Compare");
            compare.addActionListener( new ActionListener() 
            {
                @Override
                public void actionPerformed(ActionEvent ev) {
                    final int[] rows = table.getSelectedRows();
                    if ( rows.length != 2 ) {
                        error("You need to select exactly 2 rows");
                        return;
                    }
                    
                    final int min = Math.min(rows[0],rows[1]);
                    final int max = Math.max(rows[0],rows[1]);
                    
                    final ProfileData previous = tableModel.getRow(min);
                    final ProfileData current  = tableModel.getRow(max);
                    
                    if ( ! current.getSelectedThreadName().isPresent() ) {
                        error("Profile "+current+" has no thread selected");
                        return;
                    }                
                    
                    final Profile currentProfile = current.getSelectedProfile().get();
                    final Optional<Profile> previousProfile = previous.getProfileByThreadName( currentProfile.getThreadName() );
                    if ( ! previousProfile.isPresent() ) {
                        error("Profile "+previous+" has no thread named '"+currentProfile.getThreadName() );
                        return;
                    } 
                    final MethodStatsHelper previousResolver = new MethodStatsHelper( previous );
                    final MethodStatsHelper currentResolver = new MethodStatsHelper( current );
                    final IDataProvider<MethodStats> dataProvider = new MethodDataProvider(currentProfile, previousProfile.get(), previousResolver, currentResolver );
                    final FlameGraphRenderer<MethodStats> cmpRenderer = new FlameGraphRenderer<MethodStats>( dataProvider );
                    
                    final JPanel panel = new JPanel() 
                    {
                        @Override
                        public void paint(Graphics g) 
                        {
                            final FlameGraph<MethodStats> graph = cmpRenderer.render( getWidth() , getHeight() );
                            g.drawImage( graph.getImage() , 0 , 0 , null );
                        }
                    };
                    final JDialog tmp = new JDialog((Frame) null,"Comparison",true);
                    tmp.setDefaultCloseOperation( JDialog.DISPOSE_ON_CLOSE );
                    
                    tmp.add( panel );
                    
                    tmp.setPreferredSize( new Dimension(400, 400 ) );
                    tmp.pack();
                    tmp.setLocationRelativeTo(null);
                    tmp.setVisible(true);
                }
            });            
            
            table.setPreferredSize( new Dimension(400,400 ) );
            panel.add( new JScrollPane(table ) );
            panel.add( closeDialog );
            panel.add( compare );
            add( panel );
            
            table.addMouseListener( new MouseAdapter() {
                
                public void mouseClicked(MouseEvent e) 
                {
                    if ( e.getClickCount() >= 2 && e.getButton() == MouseEvent.BUTTON1 ) 
                    {
                        final int row = table.rowAtPoint( e.getPoint() );
                        if ( row != -1 ) 
                        {
                            final ProfileData clicked = tableModel.getRow(row);
                            history.jumpTo( clicked );
                        }
                    }
                }
            });
        }
        
        @Override
        public void setVisible(boolean b) 
        {
            if ( b ) {
                pack();
                setLocationRelativeTo( null );
            }
            super.setVisible(b);
        }

        @Override
        public void viewChanged(Optional<ProfileData> data,boolean triggeredFromComboBox) 
        {
            tableModel.viewChanged(data, triggeredFromComboBox);
            if ( data.isPresent() ) 
            {
                for ( int row = 0 ; row < tableModel.getRowCount() ; row++ ) 
                {
                    if ( tableModel.getRow( row ) == history.current().get() ) 
                    {
                        table.getSelectionModel().setSelectionInterval( row ,row );
                    }
                }
            }
        }
    }
    
    private void showHistory() 
    {
        historyDialog.setVisible( true );
    }
    
    private void loadProfiles() 
    {
        final JFileChooser chooser;
        final Optional<File> latestFile = history.latestFile();
        if ( latestFile.isPresent() ) 
        {
            chooser = new JFileChooser( latestFile.get() );
            chooser.setSelectedFile( latestFile.get() );
        } else {
            chooser = new JFileChooser();
        }
        if ( chooser.showOpenDialog( null ) == JFileChooser.APPROVE_OPTION ) 
        {
            loadProfiles( chooser.getSelectedFile() );
        }
    }
    
    private void loadProfiles(File file) 
    {
        try ( FileInputStream in =new FileInputStream(file) )
        {
            history.add( file , new XMLSerializer().load( in ) );
        } 
        catch(Exception e) 
        {
            error("Failed to load data from "+file.getAbsolutePath()+" ("+e.getMessage()+")",e);
        }
    }
    
    private void exportImage() {

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
    
    protected static void error(String message) 
    {
        error(message,null);
    }
    
    protected static void error(String message,Throwable t) 
    {
        if ( t != null ) {
            t.printStackTrace();
        }
        JOptionPane.showMessageDialog(null, message , "Error", JOptionPane.ERROR_MESSAGE );
    }

    protected static void info(String message) {
        JOptionPane.showMessageDialog(null, message , "Information", JOptionPane.INFORMATION_MESSAGE );
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
