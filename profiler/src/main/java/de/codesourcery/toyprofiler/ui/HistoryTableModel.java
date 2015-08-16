package de.codesourcery.toyprofiler.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.swing.table.DefaultTableModel;

import de.codesourcery.toyprofiler.ui.ViewingHistory.IViewChangeListener;

public final class HistoryTableModel extends DefaultTableModel implements IViewChangeListener
{
    private final List<ProfileData> data = new ArrayList<>();
    private final List<Column> columns = new ArrayList<>();
    
    private ViewingHistory history = new ViewingHistory();
    
    public HistoryTableModel() 
    {
        int idx = 0;
        columns.add( new Column(idx++,"Active",Boolean.class ) 
        {
            @Override
            public Object getValue(ProfileData row) {
                return history.current().isPresent() && history.current().get() == row;
            }
        });
        columns.add( new Column(idx++,"Needs save?",Boolean.class ) 
        {
            @Override
            public Object getValue(ProfileData row) {
                return row.isDirty();
            }
        });
        
        columns.add( new Column(idx++,"File",String.class ) 
        {
            @Override
            public Object getValue(ProfileData row) {
                return row.getSourceFile().map( f -> f.getAbsolutePath() ).orElse("<no file>");
            }
        });
        columns.add( new Column(idx++,"Timestamp",String.class ) 
        {
            @Override
            public Object getValue(ProfileData row) {
                return row.getTimestamp().map( FlameGraphViewer.DATE_PATTERN::format ).orElse( "<no timestamp>");
            }
        });
        columns.add( new Column(idx++,"Description",String.class ) 
        {
            @Override
            public Object getValue(ProfileData row) {
                return row.getDescription().orElse("");
            }
            @Override
            public boolean isEditable(ProfileData row) {
                return row.getSelectedProfile().isPresent();
            }
            @Override
            public void setValueAt(Object value, ProfileData row) {
                row.setDescription( (String) value );
            };
        });
    }
    
    public Object getValueAt(int row, int column) 
    {
        return getColumn(column).getValue( getRow(row ) );
    }
    
    private Column getColumn(int idx) {
        return columns.get(idx);
    }
    
    protected abstract class Column 
    {
        public final int index;
        public final String name;
        public final Class<?> valueType;
        
        public Column(int idx,String name,Class<?> valueType) {
            this.index = idx;
            this.name = name;
            this.valueType = valueType;
        }
        
        public abstract Object getValue(ProfileData row);
        
        public boolean isEditable(ProfileData row) {
            return false;
        }
        
        public void setValueAt(Object value,ProfileData row) {
            throw new UnsupportedOperationException("Cannot set value on column '"+name+"'");
        }
    }
    
    public String getColumnName(int column) 
    {
        return getColumn(column).name;
    }
    
    public ViewingHistory getHistory() {
        return history;
    }
    
    public void setHistory(ViewingHistory history) 
    {
        this.history.removeListener( this );
        this.history = history;
        this.history.addListener( this );
        fireTableDataChanged();
    }        
    
    @Override
    public int getRowCount() {
        return data != null ? data.size() : 0;
    }
    
    public int getColumnCount() {
        return columns.size();
    }
    
    public java.lang.Class<?> getColumnClass(int column) 
    {
        return getColumn(column).valueType;
    }
    
    public ProfileData getRow(int row) {
        return data.get(row);
    }
    
    @Override
    public boolean isCellEditable(int row, int column) 
    {
        return getColumn(column).isEditable( getRow(row ) );
    }
    
    @Override
    public void setValueAt(Object aValue, int row, int column) 
    {
        getColumn( column ).setValueAt( aValue , getRow(row) );
        fireTableRowsUpdated( row , row );
    }

    @Override
    public void viewChanged(Optional<ProfileData> data,boolean triggeredFromComboBox) 
    {
        this.data.clear();
        this.data.addAll( history.getItems() );
        fireTableDataChanged();
    }
}