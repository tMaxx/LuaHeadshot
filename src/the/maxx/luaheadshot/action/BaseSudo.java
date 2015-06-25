package the.maxx.luaheadshot.action;

import the.maxx.luaheadshot.MpiMessage;
import the.maxx.luaheadshot.ThePlayerSudo;

/**
 * Same as for {@link Base}, and:
 * - only used for PlayerSudo
 */
public abstract class BaseSudo
{
	protected ThePlayerSudo player;

	public BaseSudo(ThePlayerSudo player) { this.player = player; }

	public boolean processMessage(MpiMessage msg) { return false; }

	public void nextBroadcast()
	{
		player.node.icomm.BroadcastSend(new MpiMessage(MpiMessage.Type.CYCLE_NOOP));
	}

	public abstract void run();

	public void reset() {}

	public void finish() {}
}
