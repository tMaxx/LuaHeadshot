package the.maxx.luaheadshot.action.state;

/** Coordinate negotiations */
public enum ESendReceive
{
	INIT,
	/** root: bcast previous, nodes: receive */
	STATE_PREVIOUS,
	/** root: bcast proposed, nodes: receive */
	STATE_PROPOSED,
	/** root: bcast killed ones, nodes: receive */
	STATE_KILLED,
	/** nodes: calculate, send results, get to broadcast, root: receive, broadcast */
	WAIT_FINISH,
	ROOT_HANDOVER,
}
