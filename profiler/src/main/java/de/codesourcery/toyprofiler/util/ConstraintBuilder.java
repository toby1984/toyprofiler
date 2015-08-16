package de.codesourcery.toyprofiler.util;

import java.awt.GridBagConstraints;
import java.awt.Insets;

public class ConstraintBuilder {

    private int gridx;
    private int gridy;
    
    private double weightx;
    private double weighty;
    
    private int gridwidth=1;
    private int gridheight=1;
    
    private final Insets insets = new Insets(5,5,5,5);
    
    private int fill = GridBagConstraints.NONE;
    private int anchor = GridBagConstraints.CENTER;
    
    public ConstraintBuilder(int x,int y) {
        this.gridx = x;
        this.gridy = y;
    }
    
    public ConstraintBuilder weightX(double weight) 
    {
        if ( weight != 0 ) 
        {
            if ( this.fill != GridBagConstraints.BOTH) 
            {
                if ( this.fill == GridBagConstraints.VERTICAL ) 
                {
                    this.fill = GridBagConstraints.BOTH;
                } else {
                    this.fill = GridBagConstraints.HORIZONTAL;
                }
            }
        }
        this.weightx = weight;
        return this;
    }
    
    public ConstraintBuilder anchorRight() {
        this.anchor = GridBagConstraints.EAST;
        return this;
    }
    
    public ConstraintBuilder anchorLeft() {
        this.anchor = GridBagConstraints.WEST;
        return this;
    }
    
    public ConstraintBuilder anchorTop() {
        this.anchor = GridBagConstraints.NORTH;
        return this;
    }  
    
    public ConstraintBuilder anchorBottom() {
        this.anchor = GridBagConstraints.SOUTH;
        return this;
    }      
    
    public ConstraintBuilder weightY(double weight) 
    {
        if ( weight != 0 ) 
        {
            if ( this.fill != GridBagConstraints.BOTH) 
            {
                if ( this.fill == GridBagConstraints.HORIZONTAL ) 
                {
                    this.fill = GridBagConstraints.BOTH;
                } else {
                    this.fill = GridBagConstraints.VERTICAL;
                }
            }
        }
        this.weightx = weight;
        return this;
    }    
    
    public ConstraintBuilder width(int width) {
        this.gridwidth = width;
        return this;
    }
    
    public ConstraintBuilder height(int h) {
        this.gridheight = h;
        return this;
    }
    
    public GridBagConstraints build() 
    {
        final GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.fill = this.fill;
        cnstrs.insets = new Insets( insets.top , insets.left ,insets.bottom , insets.right );
        cnstrs.anchor = this.anchor;
        
        cnstrs.gridheight = gridheight;
        cnstrs.gridwidth = gridwidth;
        
        cnstrs.gridx = this.gridx;
        cnstrs.gridy = this.gridy;
        
        cnstrs.weightx = this.weightx;
        cnstrs.weighty = this.weighty;
                
        return cnstrs;
    }
}
