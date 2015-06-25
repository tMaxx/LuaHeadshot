package the.maxx.luaheadshot.action;

import the.maxx.luaheadshot.Log;
import the.maxx.luaheadshot.MpiMessage;
import the.maxx.luaheadshot.ThePlayerSudo;
import the.maxx.luaheadshot.action.state.*;

/** Play and manage ping-pong game inside environment */
public class PingPongSudo extends BaseSudo
{
	public PingPongSudo(ThePlayerSudo player)
	{
		super(player);
	}

	protected EPingPong state = EPingPong.INIT;
	protected MpiMessage.Type last = MpiMessage.Type.TEST_PONG;

	@Override
	public boolean processMessage(MpiMessage msg)
	{
		if (msg.messageType == MpiMessage.Type.TEST_PING
			|| msg.messageType == MpiMessage.Type.TEST_PONG)
		{
			state = EPingPong.SEND;
			last = msg.messageType;
			return true;
		}
		return false;
	}

	@Override
	public void run()
	{
		if (state == EPingPong.SEND)
		{
			state = EPingPong.WAIT;
			MpiMessage.Type type = PingPong.invertLast(last);

			Log.Debug(type.name() + " to #1");
			player.node.icomm.SendTo(new MpiMessage(type), 1);
		}

		if (state == EPingPong.INIT)
		{
			Log.Debug("PingPong: commencing game!");

			state = EPingPong.SEND;
			player.node.icomm.BroadcastSend(new MpiMessage(MpiMessage.Type.TEST_INIT_PINGPONG));
		}
		else
			super.nextBroadcast();
	}
}
