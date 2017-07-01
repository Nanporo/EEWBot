package net.teamfruit.eewbot;

public class Channel {
	public static final Channel DEFAULT = new Channel(0);

	private final long id;
	public boolean eewAlert = true;
	public boolean eewPrediction = false;

	public Channel(final long id) {
		this.id = id;
	}

	public long getId() {
		return this.id;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("EEW警報: ").append(this.eewAlert).append("\n");
		sb.append("EEW予報: ").append(this.eewPrediction);
		return sb.toString();
	}
}
