package moe.rtd.discord.roleassignerbot.reaction;

import moe.rtd.discord.roleassignerbot.DiscordConnection;
import moe.rtd.discord.roleassignerbot.config.MessageConfiguration;
import moe.rtd.discord.roleassignerbot.config.Terminable;
import moe.rtd.discord.roleassignerbot.util.DataFormatter;
import sx.blah.discord.handle.impl.events.guild.channel.message.reaction.ReactionAddEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.reaction.ReactionEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.reaction.ReactionRemoveEvent;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IReaction;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.handle.obj.IUser;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Processes the reaction events for the message it's assigned to.
 * @author Big J
 */
public class ReactionHandler implements Runnable, Terminable {

    /**
     * Queue for storing reaction events to be processed.
     */
    private final BlockingQueue<ReactionEvent> queue;

    /**
     * Reference to the message configuration that this reaction handler is assigned to.
     */
    private final MessageConfiguration messageConfiguration;

    /**
     * Thread which processes the reaction events.
     */
    private final Thread thread;
    /**
     * Whether or not this reaction handler has been terminated.
     */
    private volatile boolean terminated = false;

    /**
     * Constructs a new reaction handler.
     */
    public ReactionHandler(MessageConfiguration messageConfiguration) {
        this.queue = new ArrayBlockingQueue<>(128);
        this.messageConfiguration = messageConfiguration;
        this.thread = new Thread(this);
    }

    /**
     * Starts the reaction handler thread.
     * @throws IllegalThreadStateException – if the thread was already started.
     * @see Thread#start()
     */
    public void start() {
        thread.start();
    }

    /**
     * Processes the reaction events in the queue.
     */
    @Override
    public void run() {
        process();
        System.out.println("Reaction handler for message " + DataFormatter.format(messageConfiguration) + " has started."); // TODO replace with log4j
        kill: while(!terminated) {
            while(!Thread.interrupted()) {
                if(terminated) break kill;
                try {
                    handle(queue.take());
                } catch (InterruptedException e) {
                    System.err.println("Reaction handler for message " + DataFormatter.format(messageConfiguration) + " has been interrupted."); // TODO replace with log4j
                    break;
                }
            }
        }
        System.out.println("Reaction handler for message " + DataFormatter.format(messageConfiguration) + " has been terminated."); // TODO replace with log4j
    }

    /**
     * Processes all reactions on startup; catches up.
     * Adds all roles that need to be added, and removes roles that need to be removed.
     */
    private void process() {
        if(terminated) return;

        var client = DiscordConnection.getClient();
        var channel = messageConfiguration.getParent();
        var server = channel.getParent();
        var message = client.getGuildByID(server.getID()).getChannelByID(channel.getID()).getMessageByID(messageConfiguration.getID());

        List<IReaction> reactions = message.getReactions();

        for(IReaction r : reactions) {
            if(messageConfiguration.isUsed(r.getEmoji().getLongID())) {
                IRole ROLE = message.getGuild().getRoleByID(messageConfiguration.getRole(r.getEmoji().getLongID()));
                // FOR EACH CONFIGURED REACTION:

                for(IUser u : r.getUsers()) {
                    // ADD ALL MISSING ROLES
                    if(!(u.hasRole(ROLE))) u.addRole(ROLE);
                }

                for(IUser u : message.getGuild().getUsersByRole(ROLE)) {
                    // REMOVE ALL UNWANTED ROLES
                    if(!(r.getUserReacted(u))) u.removeRole(ROLE);
                }
            }
        }
    }

    /**
     * Handles a reaction event.
     * @param reactionEvent The reaction event to handle.
     */
    private void handle(ReactionEvent reactionEvent) throws InterruptedException {
        if(terminated) throw new InterruptedException();

        if(reactionEvent instanceof ReactionAddEvent) {
            var e = (ReactionAddEvent) reactionEvent;

            long EMOTE = e.getReaction().getEmoji().getLongID();
            if(messageConfiguration.isUsed(EMOTE)) {
                long ROLE = messageConfiguration.getRole(EMOTE);
                e.getUser().addRole(e.getGuild().getRoleByID(ROLE));
            }

        } else if(reactionEvent instanceof ReactionRemoveEvent) {
            var e = (ReactionRemoveEvent) reactionEvent;

            long EMOTE = e.getReaction().getEmoji().getLongID();
            if(messageConfiguration.isUsed(EMOTE)) {
                long ROLE = messageConfiguration.getRole(EMOTE);
                e.getUser().removeRole(e.getGuild().getRoleByID(ROLE));
            }

        } else throw new RuntimeException("ReactionEvent is neither one of the two known subclasses.");
    }

    /**
     * Puts an event at the end of the queue.
     * @param reactionEvent The event to add to the queue.
     */
    public void accept(ReactionEvent reactionEvent) throws InterruptedException {
        if(terminated) return;
        queue.put(reactionEvent);
    }

    /**
     * Terminates this reaction handler, clears the queue and tries to stop its thread immediately.
     */
    @Override
    public void terminate() {
        if(terminated) return;
        synchronized(thread) {
            if(terminated) return;
            terminated = true;
            thread.interrupt();
            queue.clear();
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException("Terminator has been interrupted.");
            }
        }
    }
}