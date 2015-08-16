package de.codesourcery.toyprofiler.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import de.codesourcery.toyprofiler.MethodStatsHelper;
import de.codesourcery.toyprofiler.Profile.MethodStats;
import de.codesourcery.toyprofiler.ui.FlameGraphPanel.IMethodStatSelectionListener;

public final class SelectionInfoPanel extends JPanel implements IMethodStatSelectionListener 
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

    @Override
    public void selectionChanged(MethodStats s , MethodStatsHelper resolver ) {
        className.setText( s == null ? "" : resolver.getClassName(s) );
        methodName.setText( s == null ? "" : resolver.getMethodName(s)+"("+resolver.getMethodSignature(s)+")" );
        invocations.setText( s == null ? "" : FlameGraphViewer.INVOCATION_COUNT_FORMAT.format( s.getInvocationCount() ) );
        totalTime.setText( s == null ? "" : FlameGraphViewer.millisToString( s.getTotalTimeMillis() ) );
        ownTime.setText( s == null ? "" : FlameGraphViewer.millisToString( s.getOwnTimeMillis() ) );            
    }
}