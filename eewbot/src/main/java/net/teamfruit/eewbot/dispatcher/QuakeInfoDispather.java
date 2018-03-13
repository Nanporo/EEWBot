package net.teamfruit.eewbot.dispatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jsoup.Jsoup;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.event.QuakeInfoEvent;
import net.teamfruit.eewbot.node.QuakeInfo;

public class QuakeInfoDispather implements Runnable {

	public static final QuakeInfoDispather INSTANCE = new QuakeInfoDispather();

	public static final String REMOTE = "https://typhoon.yahoo.co.jp/weather/earthquake/";

	private QuakeInfo prev;

	private QuakeInfoDispather() {
	}

	@Override
	public void run() {
		try {
			final QuakeInfo info = get(REMOTE);
			if (!info.equals(this.prev)) {
				if (this.prev!=null) {
					EEWBot.instance.getClient().getDispatcher().dispatch(new QuakeInfoEvent(EEWBot.instance.getClient(), info, info.useDataEquals(this.prev)));
					if (EEWBot.instance.getConfig().isDebug())
						Files.write(Paths.get("debug", String.valueOf(System.currentTimeMillis())), Arrays.asList(info.getOriginal().outerHtml().split("\n")));
				}
				this.prev = info;
			}
		} catch (final IOException e) {
			Log.logger.error(ExceptionUtils.getStackTrace(e));
		}
	}

	public static QuakeInfo get(final String remote) throws IOException {
		return new QuakeInfo(Jsoup.connect(remote).get());
	}

}
