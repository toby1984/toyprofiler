package de.codesourcery.toyprofiler.ui;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
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
import de.codesourcery.toyprofiler.ui.FlameGraphRenderer.FlameGraph;
import de.codesourcery.toyprofiler.ui.FlameGraphRenderer.IDataProvider;
import de.codesourcery.toyprofiler.ui.FlameGraphViewer.MethodDataProvider;
import de.codesourcery.toyprofiler.ui.ViewingHistory.IViewChangeListener;
import de.codesourcery.toyprofiler.util.IGridBagHelper;

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
    
    public HistoryDialog(Preferences preferences) 
    {
        this.preferences = preferences;
        setDefaultCloseOperation( JDialog.HIDE_ON_CLOSE );

        final JPanel panel = new JPanel();
        final JButton closeDialog = new JButton("Hide window");
        closeDialog.addActionListener( ev -> 
        {
            setVisible(false);
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
        
        table.setPreferredSize( new Dimension(400,400 ) );
        panel.add( new JScrollPane(table ) );
        panel.add( closeDialog );
        panel.add( compare );
        panel.add( unloadProfile );
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
                        tableModel.getHistory().jumpTo( clicked );
                    }
                }
            }
        });
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
        
        final JPanel panel = new JPanel() 
        {
            protected void paintComponent(Graphics g) {
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