package net.teamfruit.eewbot;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;

import net.teamfruit.eewbot.dispatcher.EEWEvent;
import net.teamfruit.eewbot.dispatcher.MonitorEvent;
import net.teamfruit.eewbot.dispatcher.QuakeInfoEvent;
import net.teamfruit.eewbot.node.EEW;
import net.teamfruit.eewbot.node.QuakeInfo;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;

public class EEWEventListener {

	@EventSubscriber
	public void onEEW(final EEWEvent e) {
		final EEW eew = e.getEEW();
		for (final Iterator<Entry<Long, CopyOnWriteArrayList<Channel>>> it1 = EEWBot.instance.getChannels().entrySet().iterator(); it1.hasNext();) {
			final Entry<Long, CopyOnWriteArrayList<Channel>> entry = it1.next();
			for (final Iterator<Channel> it2 = entry.getValue().iterator(); it2.hasNext();) {
				final Channel channel = it2.next();
				if ((eew.isAlert()&&channel.eewAlert)||(!eew.isAlert()&&channel.eewPrediction)) {
					final IGuild id = EEWBot.instance.getClient().getGuildByID(entry.getKey());
					final IChannel c = id.getChannelByID(channel.getId());
					c.sendMessage(eew.buildEmbed());
				}
			}
		}
	}

	@EventSubscriber
	public void onQuakeInfo(final QuakeInfoEvent e) {
		final QuakeInfo info = e.getQuakeInfo();
		for (final Iterator<Entry<Long, CopyOnWriteArrayList<Channel>>> it1 = EEWBot.instance.getChannels().entrySet().iterator(); it1.hasNext();) {
			final Entry<Long, CopyOnWriteArrayList<Channel>> entry = it1.next();
			for (final Iterator<Channel> it2 = entry.getValue().iterator(); it2.hasNext();) {
				final Channel channel = it2.next();
				if (channel.quakeInfo) {
					final IGuild id = EEWBot.instance.getClient().getGuildByID(entry.getKey());
					final IChannel c = id.getChannelByID(channel.getId());
					c.sendMessage(info.buildEmbed());
				}
			}
		}
	}

	@EventSubscriber
	public void onMonitor(final MonitorEvent e) {
		for (final Iterator<Entry<Long, CopyOnWriteArrayList<Channel>>> it1 = EEWBot.instance.getChannels().entrySet().iterator(); it1.hasNext();) {
			final Entry<Long, CopyOnWriteArrayList<Channel>> entry = it1.next();
			for (final Iterator<Channel> it2 = entry.getValue().iterator(); it2.hasNext();) {
				final Channel channel = it2.next();
				final IGuild id = EEWBot.instance.getClient().getGuildByID(entry.getKey());
				final IChannel c = id.getChannelByID(channel.getId());
				c.sendFile("", e.getImage(), "kyoshinmonitor.png");
			}
		}
	}
}