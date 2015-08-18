package de.codesourcery.toyprofiler.ui;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import de.codesourcery.toyprofiler.MethodStatsHelper;
import de.codesourcery.toyprofiler.Profile;
import de.codesourcery.toyprofiler.Profile.MethodStats;
import de.codesourcery.toyprofiler.ui.FlameGraphRenderer.IDataProvider;
import de.codesourcery.toyprofiler.ui.FlameGraphViewer.MethodDataProvider;
import de.codesourcery.toyprofiler.ui.ViewingHistory.IViewChangeListener;
import de.codesourcery.toyprofiler.util.IGridBagHelper;
import de.codesourcery.toyprofiler.util.IProfileIOAdapter;

public final class HistoryDialog extends JDialog implements IViewChangeListener , IGridBagHelper
{
    private final HistoryTableModel tableModel = new HistoryTableModel();
    private final JTable table = new JTable( tableModel);
    
    private final Preferences preferences;
    
    private ProfileData[] getSelectedRows() 
    {
        final int[] rows = table.getSelectedRows();
        final ProfileData[] result;
        if ( rows != null && rows.length > 0 ) 
        {
            result = new ProfileData[rows.length];
            Arrays.sort( rows );
            int ptr = 0;
            for ( int idx : rows ) 
            {
                result[ptr++] = tableModel.getRow( idx );
            }
        } else {
            result = new ProfileData[0];
        }
        return result;
    }
    
    public HistoryDialog(Preferences preferences,IProfileIOAdapter ioAdapter) 
    {
        super((Frame) null, "History", false );
        
        this.preferences = preferences;
        setDefaultCloseOperation( JDialog.HIDE_ON_CLOSE );

        final JPanel panel = new JPanel();
        panel.setLayout( new GridBagLayout() );
        
        final JButton closeDialog = new JButton("Hide window");
        closeDialog.addActionListener( ev -> 
        {
            setVisible(false);
        });
        
        final JButton load = new JButton("Load");
        load.addActionListener( ev -> 
        {
            try {
                FlameGraphViewer.loadProfiles( preferences , tableModel.getHistory() , ioAdapter );
            } catch (Exception e1) {
                error("Failed to load files");
            }
        });
        
        
        final JButton moveUp = new JButton("Up");
        moveUp.addActionListener( ev -> 
        {
            final ProfileData[] selection = getSelectedRows();
            Arrays.stream( selection ).forEach( s ->  tableModel.getHistory().moveUp( selection[0] ) );
        });
        
        final JButton moveDown = new JButton("Down");
        moveDown.addActionListener( ev -> 
        {
            final ProfileData[] selection = getSelectedRows();
            Arrays.stream( selection ).forEach( s ->  tableModel.getHistory().moveDown( selection[0] ) );
        });        
        
        
        
        final JButton unloadProfile = new JButton("Unload");
        unloadProfile.addActionListener( ev -> 
        {
            final int[] rows = table.getSelectedRows();
            final List<ProfileData> toUnload = Arrays.stream( rows ).mapToObj( idx -> tableModel.getRow(idx) ).collect( Collectors.toList() );
            tableModel.getHistory().unload( toUnload );
        });            
        
        final JButton compare = new JButton("Compare");
        compare.addActionListener( ev -> compareProfiles() );
        
        table.setPreferredSize( new Dimension(470,400 ) );
        panel.add( new JScrollPane(table ) , IGridBagHelper.cnstrs(0,0).weightX(1).weightY(1).build() );
        
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout( new GridBagLayout() );
        
        buttonPanel.add( load     , IGridBagHelper.cnstrs(0,0).weightX(1.0).marginBottom(20).build() );
        buttonPanel.add( moveUp   , IGridBagHelper.cnstrs(0,1).weightX(1.0).noMargin().build() );
        buttonPanel.add( moveDown , IGridBagHelper.cnstrs(0,2).weightX(1.0).noMargin().marginBottom(20).build() );

        buttonPanel.add( compare  , IGridBagHelper.cnstrs(0,3).weightX(1.0).noMargin().marginBottom(5).marginTop(5).build() );
        buttonPanel.add( closeDialog   , IGridBagHelper.cnstrs(0,4).weightX(1.0).noMargin().build() );
        buttonPanel.add( unloadProfile , IGridBagHelper.cnstrs(0,5).weightX(1.0).noMargin().build() );
        
        
        panel.add( buttonPanel , IGridBagHelper.cnstrs(1,0).build() );
        
        add( panel );
        
        final MouseAdapter mouseListener = new MouseAdapter() {
            
            public void mouseClicked(MouseEvent e) 
            {
                if ( e.getClickCount() >= 2 && e.getButton() == MouseEvent.BUTTON1 ) 
                {
                    final int row = table.rowAtPoint( e.getPoint() );
                    if ( row != -1 ) 
                    {
                        final ProfileData clicked = tableModel.getRow(row);
                        tableModel.getHistory().jumpTo( clicked );
                    }
                }
            }
            public void mouseMoved(MouseEvent e) 
            {
                final int row = table.rowAtPoint( e.getPoint() );
                if ( row != -1 ) 
                {
                    final ProfileData hovered = tableModel.getRow(row);
                    if ( hovered.hasFile() ) 
                    {
                        table.setToolTipText( hovered.getSourceFile().get().getAbsolutePath() );
                        return;
                    }
                } 
                table.setToolTipText( null );
            }
        };
        table.addMouseListener( mouseListener);
        table.addMouseMotionListener( mouseListener);
    }
    
    private void compareProfiles() 
    {
        final ProfileData[] rows = getSelectedRows();
        if ( rows.length != 2 ) {
            error("You need to select exactly 2 rows");
            return;
        }
        
        final ProfileData previous = rows[0];
        final ProfileData current  = rows[1];
        try {
            compareProfiles(previous,current,preferences);
        } catch (Exception e) {
            error( e.getMessage() );
        }
    }
    
    public static void compareProfiles(ProfileData previous,ProfileData current,Preferences preferences) throws Exception
    {
        if ( ! current.getSelectedThreadName().isPresent() ) {
            throw new Exception("Profile "+current+" has no thread selected");
        }                
        
        final Profile currentProfile = current.getSelectedProfile().get();
        final Optional<Profile> previousProfile = previous.getProfileByThreadName( currentProfile.getThreadName() );
        if ( ! previousProfile.isPresent() ) {
            throw new Exception("Profile "+previous+" has no thread named '"+currentProfile.getThreadName() );
        } 
        final MethodStatsHelper previousResolver = new MethodStatsHelper( previous );
        final MethodStatsHelper currentResolver = new MethodStatsHelper( current );
        final IDataProvider<MethodStats> dataProvider = new MethodDataProvider(currentProfile, previousProfile.get(), previousResolver, currentResolver );
        
        final FlameGraphRenderer<MethodStats> cmpRenderer = new FlameGraphRenderer<MethodStats>( dataProvider , preferences.getDefaultCompareColorScheme() );
        
        final SelectionInfoPanel infoPanel = new SelectionInfoPanel();
        
        final FlameGraphPanel graphPanel = new FlameGraphPanel(cmpRenderer,dataProvider,currentResolver,preferences) 
        {
            protected Map<String, String> createToolTipMap(MethodStats stats) 
            {
                final String key = "Time difference to other profile";
                
                final Map<String, String> map = createToolTipMap(stats,currentResolver);
                try {
                    double currentValue = dataProvider.getPercentageValue( stats );
                    double previousValue = dataProvider.getPreviousPercentageValue( stats );
                    double delta = 100*(currentValue - previousValue);
                    map.put(key, FlameGraphViewer.PERCENTAGE_FORMAT.format( delta )+" %" );
                } catch(NoSuchElementException | IllegalStateException e) {
                    map.put(key,"<comparison failed, different call flows>");
                }
                return map;
            }       
            
            @Override
            protected ColorScheme getColorScheme() 
            {
                return preferences.getDefaultCompareColorScheme();
            }
        };
        graphPanel.setPreferredSize( new Dimension(400,400 ) );
        
        graphPanel.addListener(infoPanel);
        
        final JPanel container = new JPanel();
        container.setLayout( new GridBagLayout() );
        container.add( infoPanel , IGridBagHelper.cnstrs(0,0).weightX(1.0).build() );
        container.add( graphPanel , IGridBagHelper.cnstrs(0,1).weightX(1.0).weightY(1.0).resizeBoth().build() );
        
        final JDialog tmp = new JDialog((Frame) null,"current: "+current+" / previous: "+previous,true);
        tmp.setDefaultCloseOperation( JDialog.DISPOSE_ON_CLOSE );
        
        tmp.add( container );
        
        tmp.setPreferredSize( new Dimension(400, 400 ) );
        tmp.pack();
        tmp.setLocationRelativeTo(null);
        tmp.setVisible(true);
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
                if ( tableModel.getRow( row ) == tableModel.getHistory().current().get() ) 
                {
                    table.getSelectionModel().setSelectionInterval( row ,row );
                }
            }
        }
    }

    public void setHistory(ViewingHistory history) {
        tableModel.setHistory( history );
    }
}