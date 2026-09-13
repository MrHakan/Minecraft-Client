package me.mrhakan.agalarhack.ui.components;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
public final class ToggleSwitch {
    private ToggleSwitch() { }
    public static Button create(int x,int y,int width,BooleanSupplier value,Consumer<Boolean> change){
        return Button.builder(Component.literal(value.getAsBoolean()?"● ON":"○ OFF"),button->{
            change.accept(!value.getAsBoolean());button.setMessage(Component.literal(value.getAsBoolean()?"● ON":"○ OFF"));
        }).bounds(x,y,width,20).build();
    }
}
