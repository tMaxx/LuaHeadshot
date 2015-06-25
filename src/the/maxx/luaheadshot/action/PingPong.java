package the.maxx.luaheadshot.action;

import the.maxx.luaheadshot.Log;
import the.maxx.luaheadshot.MpiMessage;
import the.maxx.luaheadshot.ThePlayer;

/** Ping pong player for ordinary node */
public class PingPong extends Base
{
	public PingPong(ThePlayer player)
	{
		super(player);
	}

	public static MpiMessage.Type invertLast(MpiMessage.Type pp)
	{
		if (pp == MpiMessage.Type.TEST_PING)
			return MpiMessage.Type.TEST_PONG;
		else
			return MpiMessage.Type.TEST_PING;
	}

	@Override
	public boolean processMessage(MpiMessage msg)
	{
		if (msg.messageType == MpiMessage.Type.TEST_PING
				|| msg.messageType == MpiMessage.Type.TEST_PONG)
		{
			int next = (player.node.rank + 1) % player.node.size;
			MpiMessage.Type type = invertLast(msg.messageType);

			Log.Debug(type.name() + " to #" + next);

			player.node.icomm.SendTo(new MpiMessage(type), next);
			return true;
		}
		return false;
	}

	@Override
	public void run()
	{
		MpiMessage msg;
		msg = player.node.icomm.BroadcastRecv();
		if (msg != null)
			player.processMessage(msg);
	}
}
