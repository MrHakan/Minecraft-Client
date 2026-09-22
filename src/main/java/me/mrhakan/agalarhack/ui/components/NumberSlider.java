package me.mrhakan.agalarhack.ui.components;

import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** Uses vanilla keyboard focus/narration and drag semantics; persistence belongs to the enclosing screen. */
public final class NumberSlider extends AbstractSliderButton {
    private final String label;
    private final double minimum, maximum;
    private final DoubleConsumer changed;
    private final StringBuilder message = new StringBuilder(32);
    public NumberSlider(int x,int y,int width,String label,double minimum,double maximum,double initial,DoubleConsumer changed) {
        super(x,y,width,20,Component.literal(label),maximum<=minimum?0:Math.max(0,Math.min(1,(initial-minimum)/(maximum-minimum))));
        this.label=label;this.minimum=minimum;this.maximum=maximum;this.changed=changed;updateMessage();
    }
    private double actual(){return Math.round((minimum+value*(maximum-minimum))*10000.0)/10000.0;}
    @Override protected void updateMessage(){
        double current=actual();
        message.setLength(0);
        message.append(label).append(": ");
        // Keep useful precision for small ranges without the Formatter allocation incurred on every drag step.
        double rounded2=Math.round(current*100.0)/100.0;
        if(Math.abs(current-rounded2)<0.0000001) message.append(rounded2);
        else message.append(current);
        setMessage(Component.literal(message.toString()));
    }
    @Override protected void applyValue(){changed.accept(actual());}
}
