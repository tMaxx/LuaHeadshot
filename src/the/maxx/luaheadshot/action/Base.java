package the.maxx.luaheadshot.action;

import the.maxx.luaheadshot.MpiMessage;
import the.maxx.luaheadshot.ThePlayer;

/**
 * Base concept:
 * - has constructor that takes node,
 * - has method "run" that executes all code
 * - is supposed for only one 'part of equation'
 * - gets destroyed after using
 */
public abstract class Base
{
	protected ThePlayer player;
	protected boolean finished = false;

	public Base(ThePlayer player) { this.player = player; }

	public boolean processMessage(MpiMessage msg) { return false; }

	public abstract void run();

	public final boolean isFinished() { return this.finished; }

	public void finish() {}
}
