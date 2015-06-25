package the.maxx.luaheadshot.util;

import java.util.Timer;
import java.util.TimerTask;

/** Simple countdown util */
public class Countdown extends TimerTask
{
	private Timer tmr;
	private int secondsCount;
	private int countdown;

	public Countdown() {
		tmr = new Timer();
		tmr.scheduleAtFixedRate(this, 0, 500);
	}

	public Countdown(int seconds)
	{
		this();
		setSeconds(seconds);
	}

	public Countdown(int minutes, boolean ignore)
	{
		this(minutes * 60);
	}

	@Override
	public void run()
	{
		if (countdown > 0)
			countdown--;
	}

	public Countdown setHalves(int halfSecs)
	{
		if (halfSecs <= 0)
			throw new IllegalArgumentException("Parameter must not be zero or negative");

		secondsCount = halfSecs / 2;
		countdown = halfSecs;
		return this;
	}

	public Countdown setSeconds(int seconds) { return setHalves(seconds * 2); }

	public Countdown setMinutes(int minutes) { return setSeconds(minutes * 60); }

	public int getCountdown() { return countdown; }

	public int getPeriod() { return secondsCount; }

	public void start() { countdown = secondsCount; }

	public void stop() { tmr.cancel(); }

	public boolean isFinished() { return countdown <= 0; }
}
