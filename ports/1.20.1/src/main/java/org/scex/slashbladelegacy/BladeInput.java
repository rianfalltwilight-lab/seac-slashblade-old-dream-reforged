package org.scex.slashbladelegacy;

import java.util.EnumSet;
import java.util.function.Consumer;
import mods.flammpfeil.slashblade.capability.inputstate.IInputState;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;

/** Both upstream input events supply these methods. */
public interface BladeInput {
    ServerPlayer getEntity();
    IInputState getState();
    EnumSet<InputCommand> getOld();
    EnumSet<InputCommand> getCurrent();

    static void listen(EventPriority priority,Consumer<BladeInput> listener) {
        Class<? extends Event> type;
        try {
            ClassLoader loader=BladeInput.class.getClassLoader();
            Class<?> event;
            try {event=Class.forName("mods.flammpfeil.slashblade.event.handler.InputCommandEvent",false,loader);}
            catch(ClassNotFoundException oldPackage){event=Class.forName("mods.flammpfeil.slashblade.event.InputCommandEvent",false,loader);}
            type=event.asSubclass(Event.class);
        } catch(ClassNotFoundException failure) {
            throw new IllegalStateException("SlashBlade input event is missing",failure);
        }
        MinecraftForge.EVENT_BUS.addListener(priority,false,type,event->listener.accept((BladeInput)event));
    }
}
