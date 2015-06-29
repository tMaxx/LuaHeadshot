package the.maxx.luaheadshot;

import the.maxx.luaheadshot.action.BaseSudo;
import the.maxx.luaheadshot.action.PingPongSudo;
import the.maxx.luaheadshot.action.SendReceiveSudo;
import the.maxx.luaheadshot.util.Countdown;

/** ThePlayerSudo - root's handler */
public class ThePlayerSudo extends ThePlayer
{
	public ThePlayerSudo(MpiNode node)
	{
		super(node);
		Log.Info("Max players: " + (node.size - 1));
	}

	public Countdown idleCountdown = new Countdown().setMinutes(5);

	BaseSudo sudoAction = null;

	@Override
	public void reset()
	{
		state = PlayerState.IDLE;
		sudoAction = null;
	}

	@Override
	public void run()
	{
		idleCountdown.start();

		playerloop:
		while (true)
		{
			//recv all floating messages
			for (MpiMessage m : node.icomm.ReceiveAll())
			{
				if (processMessage(m))
					break playerloop;
				if (state != PlayerState.IDLE)
					sudoAction.processMessage(m);
			}

			if (node.datahub.hasPlayers())
			{ //negotiations
				if (state != PlayerState.SEND_RECEIVE)
				{
					state = PlayerState.SEND_RECEIVE;
					Log.Info("Switch mode: " + state.name());
					sudoAction = new SendReceiveSudo(this);
				}
				idleCountdown.start();
			}
			else
			{
				if (state == PlayerState.IDLE && idleCountdown.isFinished())
				{ //start ping-pong
					state = PlayerState.PINGPONG;
					Log.Info("Switch mode: " + state.name());
					sudoAction = new PingPongSudo(this);
				}
				else if (state == PlayerState.SEND_RECEIVE)
				{ //reset, return to idling
					state = PlayerState.IDLE;
					Log.Info("Switch mode: " + state.name());
					sudoAction = null;
					idleCountdown.start();
				}
			}

			if (state != PlayerState.SEND_RECEIVE)
				try { Thread.sleep(1000); }
				catch (InterruptedException ignored) {}

			if (state != PlayerState.IDLE)
				sudoAction.run();
			else
				node.icomm.BroadcastSend(new MpiMessage(MpiMessage.Type.CYCLE_NOOP));
		} //playerloop
	}
}
