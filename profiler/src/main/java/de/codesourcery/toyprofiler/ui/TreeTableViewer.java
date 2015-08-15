package de.codesourcery.toyprofiler.ui;

import java.io.FileInputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import de.codesourcery.toyprofiler.MethodStatsHelper;
import de.codesourcery.toyprofiler.Profile;
import de.codesourcery.toyprofiler.ProfileContainer;
import de.codesourcery.toyprofiler.Profile.MethodStats;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableColumn.CellDataFeatures;
import javafx.scene.control.TreeTableView;
import javafx.stage.Stage;
import javafx.util.Callback;

public class TreeTableViewer extends Application {

    public static void main(String[] args) 
    {
        Application.launch(args);
    }
    
    protected static final DecimalFormat DF = new DecimalFormat("##########0.0####");
    
    protected static final class MyTreeModel 
    {
        public final ITreeNode root;
        private final MethodStatsHelper resolver;

        public MyTreeModel(ITreeNode root,MethodStatsHelper resolver) {
            this.root = root;
            this.resolver = resolver;
        }

        public ITreeNode getRoot() {
            return root;
        }

        public int getColumnCount() {
            return 5;
        }

        public String getColumnName(int idx) {
            switch(idx) {
                case 0: return "Method";
                case 1: return "Invocations";
                case 2: return "Total time (ms)";
                case 3: return "Percentage";
                case 4: return "Own time (ms)";
                default:
                    throw new RuntimeException();
            }
        }
        
        public String getValue(ITreeNode node,int column) 
        {
            if ( node.value() instanceof Profile) 
            {
                switch( column ) {
                    case 0:
                        return ((Profile) node.value()).getThreadName();
                    default:
                        return "";
                }

            } 
            if ( node.value() instanceof MethodStats ) 
            {
                switch( column) 
                {
                    case 0:
                        return resolver.getRawMethodName( ((MethodStats) node.value() ) );
                    case 1:
                        return ""+((MethodStats) node.value()).getInvocationCount();
                    case 2:
                        return format(((MethodStats) node.value()).getTotalTimeMillis());
                    case 3:
                        return format( ((MethodStats) node.value()).getPercentageOfParentTime() );             
                    case 4:
                        return format( ((MethodStats) node.value()).getOwnTimeMillis() );       
                    default:
                }
            } 
            return "";
        }

        public TreeItem<ITreeNode> toTreeItems() 
        {
            final TreeItem<ITreeNode> result = new TreeItem<>( null );
            for ( ITreeNode child : root.children() ) {
                result.getChildren().add( createTreeItem( child ) );
            }
            return result;
        }

        private TreeItem<ITreeNode> createTreeItem(ITreeNode node) 
        {
            final TreeItem<ITreeNode> result = new TreeItem<>( node );
            for ( ITreeNode child : node.children() ) {
                result.getChildren().add( createTreeItem( child ) );
            }
            return result;
        }
    }

    protected interface ITreeNode
    {
        public List<ITreeNode> children();

        public void addChild(ITreeNode node);

        public Object value();
    }

    protected class MyTreeNode implements ITreeNode {

        public final List<ITreeNode> children = new ArrayList<>();
        public final Object value;

        public MyTreeNode(Object value) 
        {
            this.value = value;
        }

        @Override
        public Object value() {
            return value;
        }

        @Override
        public List<ITreeNode> children() {
            return children;
        }

        @Override
        public void addChild(ITreeNode node) {
            children.add( node );
        }
    }

    protected static String format(float value) {
        return DF.format( value );
    }
    
    @Override
    public void start(Stage stage)
    {
        final Parameters params = getParameters();
        final List<String> parameters = params.getRaw();
        
        if ( parameters.size() != 1 ) {
            throw new RuntimeException("Bad command line arguments, expected exactly one argument (XML file to load)");
        }
        
        final String fileToLoad = parameters.get(0);
        
        final ProfileContainer profiles;
        try {
            FileInputStream in = new FileInputStream( fileToLoad );
            profiles = Profile.load( in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final TreeTableView<ITreeNode> treeTable = createTreeTableView( profiles );

        treeTable.setPrefSize( 2000, 2000 );
        stage.setTitle("Profiling results: "+fileToLoad);

        stage.setResizable(true);
        
        final Scene scene = new Scene(new Group(), 1000, 1000);
        
        final Group sceneRoot = (Group) scene.getRoot();
        sceneRoot.setAutoSizeChildren(true);

        sceneRoot.getChildren().add(treeTable);
        
        stage.setScene(scene);
        stage.show();
    }

    private TreeTableView<ITreeNode> createTreeTableView(ProfileContainer container) 
    {
        
        final List<Profile> profiles = container.getProfiles();
        final MyTreeNode rootNode = new MyTreeNode(null);
        for ( Profile p : profiles ) 
        {
            final MyTreeNode profileNode = new MyTreeNode( p );
            rootNode.addChild( profileNode );

            profileNode.addChild( createTreeNodes( p.getTopLevelMethod() ) );
        }

        final MyTreeModel model = new MyTreeModel( rootNode , new MethodStatsHelper( container ) );

        final TreeItem<ITreeNode> root = model.toTreeItems();
        root.setExpanded(true);

        final TreeTableView<ITreeNode> treeTableView = new TreeTableView<>(root);

        for ( int i = 0 ; i < model.getColumnCount() ; i++ ) 
        {
            final int columnNo = i;
            final TreeTableColumn<ITreeNode,String> newColumn = new TreeTableColumn<>( model.getColumnName( i ) );
            
            newColumn.setCellValueFactory( new Callback<TreeTableColumn.CellDataFeatures<ITreeNode,String>, ObservableValue<String>>() {

                @Override
                public ObservableValue<String> call( CellDataFeatures<ITreeNode, String> param) 
                {
                    return new ReadOnlyStringWrapper( model.getValue( param.getValue().getValue() , columnNo ) );
                }
            });
            treeTableView.getColumns().add(newColumn);
        }

        treeTableView.setShowRoot(false);
        return treeTableView;
    }

    private ITreeNode createTreeNodes(MethodStats method) 
    {
        final MyTreeNode node = new MyTreeNode( method );
        for ( MethodStats entry : method.getCallees().values() ) 
        {
            node.addChild( createTreeNodes( entry ) );
        }
        return node;
    }
}