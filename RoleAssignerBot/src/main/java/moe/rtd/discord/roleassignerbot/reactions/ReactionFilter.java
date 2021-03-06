package moe.rtd.discord.roleassignerbot.reactions;

import moe.rtd.discord.roleassignerbot.config.Identifiable;
import moe.rtd.discord.roleassignerbot.interfaces.Terminable;
import moe.rtd.discord.roleassignerbot.misc.DataFormatter;
import moe.rtd.discord.roleassignerbot.interfaces.QueueConsumer;
import moe.rtd.discord.roleassignerbot.misc.logging.Markers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sx.blah.discord.handle.impl.events.guild.channel.message.reaction.ReactionEvent;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Abstract class for simplifying all reaction filters.
 * @author Big J
 */
public abstract class ReactionFilter<O extends Identifiable> implements QueueConsumer<ReactionEvent>, Runnable, Terminable {

    /**
     * Log4j2 Logger for this class.
     */
    protected static final Logger log = LogManager.getLogger(ReactionFilter.class);

    /**
     * Queue for buffering reaction events.
     */
    private final BlockingQueue<ReactionEvent> queue;

    /**
     * The owner of this reactions.
     */
    private final O owner;

    /**
     * Thread which processes the reaction events.
     */
    private final Thread thread;
    /**
     * Whether or not this reaction handler has been terminated.
     */
    private volatile boolean terminated = false;

    /**
     * Sets up the queue and starts the thread.
     */
    ReactionFilter(O owner) {
        this.owner = owner;
        this.queue = new ArrayBlockingQueue<>(128);
        this.thread = new Thread(this);

        thread.start();
    }

    /**
     * Filters the reaction events in the queue.
     */
    @Override
    public final void run() {
        log.debug(Markers.REACTIONS, "Reaction filter for " + DataFormatter.format(owner) + " has started.");
        while(!terminated) {
            try {
                filter(queue.take());
            } catch(InterruptedException e) {
                log.debug(Markers.REACTIONS, "Reaction filter for " + DataFormatter.format(owner) + " has been interrupted.");
                if(terminated) break;
            }
        }
        log.debug(Markers.REACTIONS, "Reaction filter for " + DataFormatter.format(owner) + " has been terminated.");
    }

    /**
     * Filter the reaction event.
     */
    protected abstract void filter(ReactionEvent reactionEvent) throws InterruptedException;

    /**
     * Puts an event at the end of the queue.
     * @param reactionEvent The reaction event to reactions.
     * @throws InterruptedException If the current thread is interrupted when waiting for a free space in the queue.
     */
    @Override
    public final void accept(ReactionEvent reactionEvent) throws InterruptedException {
        if(terminated) return;
        queue.put(reactionEvent);
    }

    /**
     * @return The owner of this instance.
     */
    final O getOwner() {
        return owner;
    }

    /**
     * Terminates this reactions.
     */
    @Override
    public final void terminate() {
        if(terminated) return;
        synchronized(thread) {
            if(terminated) return;
            terminated = true;
            thread.interrupt();
            queue.clear();
            try {
                thread.join();
            } catch(InterruptedException e) {
                throw new RuntimeException("Terminator has been interrupted.");
            }
        }
    }
}
