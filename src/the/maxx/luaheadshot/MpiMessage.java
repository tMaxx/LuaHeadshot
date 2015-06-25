package the.maxx.luaheadshot;

import java.io.Serializable;

/** MpiMessage - MpiIntercom Message */
public final class MpiMessage implements Serializable
{
	private static final long serialVersionUID = -6177156352211149855L;

	public enum Type
	{
		/** Play ping-pong with each other! For each PING recv, node must reply with PONG! */
		TEST_INIT_PINGPONG,
		TEST_PING, TEST_PONG,

		/** data: Log.Message */
		LOG_MESSAGE,

		/** node encountered error/exc and had to reset */
		NODE_RESET,
		/** root encountered an error, start from beginning */
		NODE_ALL_RESET,

		/** no operation - used to synchronize between nodes */
		CYCLE_NOOP,

		/** sent at beginning of each negotiations to all proc nodes */
		INITIALIZE_NEGOTIATIONS,
		/** data: State, user's state (verify against) */
		USER_STATE,
		/** data: boolean[], current state to negotiate */
		USER_ACCEPTANCE_STATE,
		/** reset player to starting position */
		USER_RESET_STATE,

		/** data: must have .toString(); quit gracefully! (i.e. free resources) */
		ABORT_EXECUTION,
		/** abandon all work and exit */
		FINALIZE_EXECUTION
	}

	public int fromRank;
	public Type messageType;
	public Object data = null;

	public MpiMessage() {}

	public MpiMessage(MpiMessage.Type tp)
	{
		messageType = tp;
	}
}
