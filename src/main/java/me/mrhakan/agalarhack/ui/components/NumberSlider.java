package me.mrhakan.agalarhack.ui.components;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** Uses vanilla keyboard focus/narration and drag semantics; persistence belongs to the enclosing screen. */
public final class NumberSlider extends AbstractSliderButton {
    private final String label;
    private final double minimum, maximum;
    private final DoubleConsumer changed;
    public NumberSlider(int x,int y,int width,String label,double minimum,double maximum,double initial,DoubleConsumer changed) {
        super(x,y,width,20,Component.literal(label),maximum<=minimum?0:Math.max(0,Math.min(1,(initial-minimum)/(maximum-minimum))));
        this.label=label;this.minimum=minimum;this.maximum=maximum;this.changed=changed;updateMessage();
    }
    private double actual(){return Math.round((minimum+value*(maximum-minimum))*10000.0)/10000.0;}
    @Override protected void updateMessage(){setMessage(Component.literal(label+": "+String.format(Locale.ROOT,"%.2f",actual())));}
    @Override protected void applyValue(){changed.accept(actual());}
}
